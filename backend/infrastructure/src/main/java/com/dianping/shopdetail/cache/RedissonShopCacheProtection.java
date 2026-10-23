package com.dianping.shopdetail.cache;

import com.dianping.shop.cache.ShopCacheAccessException;
import com.dianping.shop.cache.ShopCacheProtection;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redisson 共享店铺过滤器、同店锁与不存在标记适配器。
 * 初始化只追加；配置中的世代与完整加载标记绑定，位图缺失会使旧就绪状态失效。
 */
@Component
public class RedissonShopCacheProtection implements ShopCacheProtection {
    private static final Logger log = LoggerFactory.getLogger(RedissonShopCacheProtection.class);
    private static final String FILTER = "{shop-bloom}:ids";
    private static final String CONFIG = FILTER + ":config";
    private static final String READY = "{shop-bloom}:ready";
    private static final String STATE_LOCK = "{shop-bloom}:state-lock";
    private static final String LOAD_LOCK = "{shop-bloom}:load-lock";
    private final RedissonClient redisson;
    private final JdbcTemplate jdbc;
    private final long capacity;
    private final double probability;
    private final int batchSize;
    private final Duration wait;
    private final Duration nullTtl;
    private volatile boolean startupLoaded;

    /**
     * 复用应用配置的 Redisson 客户端，不为过滤器另建连接或数据库。
     */
    public RedissonShopCacheProtection(RedissonClient redisson, JdbcTemplate jdbc,
            @Value("${shop-cache.bloom-capacity:100000}") long capacity,
            @Value("${shop-cache.bloom-false-probability:0.001}") double probability,
            @Value("${shop-cache.bloom-batch-size:1000}") int batchSize,
            @Value("${shop-cache.lock-wait-ms:2000}") long waitMillis,
            @Value("${shop-cache.null-ttl-ms:120000}") long nullTtlMillis) {
        if (capacity <= 0 || probability <= 0 || probability >= 1 || batchSize <= 0
                || waitMillis < 0 || nullTtlMillis <= 0) throw new IllegalArgumentException("店铺缓存配置不合法");
        this.redisson = redisson;
        this.jdbc = jdbc;
        this.capacity = capacity;
        this.probability = probability;
        this.batchSize = batchSize;
        this.wait = Duration.ofMillis(waitMillis);
        this.nullTtl = Duration.ofMillis(nullTtlMillis);
    }

    /** 未就绪或无法取得短期状态锁时放行；只有稳定完整的数据才能作出否定判断。 */
    @Override public boolean mayExist(Long shopId) {
        try {
            Boolean result = withState(Duration.ZERO, ignored -> {
                String before = readyGeneration();
                if (before == null) return true;
                boolean possible = filter().contains(shopId);
                String after = readyGeneration();
                return possible || !before.equals(after);
            });
            return result == null || result;
        } catch (RuntimeException failure) {
            log.warn("店铺过滤器不可用，放行查询，shopId={}", shopId, failure);
            return true;
        }
    }

    /** 共享空值标记读取失败时按未命中处理。 */
    @Override public boolean isNotFound(Long shopId) {
        try {
            return redisson.getBucket(absentKey(shopId), StringCodec.INSTANCE).isExists();
        } catch (RuntimeException failure) {
            log.warn("店铺不存在标记读取失败，shopId={}", shopId, failure);
            return false;
        }
    }

    /** 使用不指定固定租约的 tryLock，由 Redisson 看门狗续期。 */
    @Override public Lease tryLock(Long shopId, Duration timeout) {
        RLock lock = redisson.getLock(lockKey(shopId));
        try {
            if (!lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) return null;
            return new Lease() {
                /** 写事务提交前检查当前线程仍拥有锁。 */
                @Override public void verifyOwnership() {
                    if (!lock.isHeldByCurrentThread()) throw unavailable("店铺锁所有权已失效", null);
                }
                /** 只释放当前线程持有的锁，连接异常留给看门狗过期机制处理。 */
                @Override public void close() {
                    try { if (lock.isHeldByCurrentThread()) lock.unlock(); }
                    catch (RuntimeException failure) { log.warn("店铺锁释放失败，shopId={}", shopId, failure); }
                }
            };
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable("店铺锁等待被中断", interrupted);
        } catch (RuntimeException failure) {
            throw unavailable("店铺锁不可用", failure);
        }
    }

    /** 新增期间追加 ID，不把未完整加载的过滤器标为就绪。 */
    @Override public void add(Long shopId) {
        try {
            Boolean added = withState(wait, ignored -> {
                prepare();
                filter().add(shopId);
                return true;
            });
            if (added == null) throw unavailable("过滤器写入锁等待超时", null);
        } catch (RuntimeException failure) {
            throw unavailable("店铺 ID 加入过滤器失败", failure);
        }
    }

    /** 在与所有权校验相同的 Lua 脚本内发布正常数据并清理不存在标记。 */
    @Override public void publish(Long id, String key, String value, Duration ttl) {
        owned(id, "redis.call('psetex',KEYS[2],ARGV[2],ARGV[3]); redis.call('del',KEYS[3]);",
                List.of(lockKey(id), key, absentKey(id)), Long.toString(ttl.toMillis()), value);
    }

    /** 只有真正查无店铺的持锁线程才能发布不存在，并移除两个旧快照。 */
    @Override public void markNotFound(Long id) {
        owned(id, "redis.call('psetex',KEYS[2],ARGV[2],'1'); redis.call('del',KEYS[3],KEYS[4]);",
                List.of(lockKey(id), absentKey(id), basicKey(id), detailKey(id)), Long.toString(nullTtl.toMillis()));
    }

    /** 写操作必须成功清理所有入口的旧缓存，不能吞掉 Redis 故障。 */
    @Override public void evict(Long id) {
        owned(id, "redis.call('del',KEYS[2],KEYS[3],KEYS[4]);",
                List.of(lockKey(id), basicKey(id), detailKey(id), absentKey(id)));
    }

    /** 启动后立即尝试加载，缺失或失败默认每 30 秒恢复；健康时不重建。 */
    @Scheduled(initialDelayString = "${shop-cache.bloom-initial-delay-ms:0}",
            fixedDelayString = "${shop-cache.bloom-retry-ms:30000}")
    public void recover() {
        RLock load = redisson.getLock(LOAD_LOCK);
        try {
            if (!load.tryLock()) return;
            try {
                String generation = withState(wait, ignored -> {
                    if (readyGeneration() != null && startupLoaded) return null;
                    script("redis.call('del',KEYS[1]); return 1", List.of(READY));
                    return prepare();
                });
                if (generation == null) return;
                long cursor = 0;
                while (true) {
                    List<Long> ids = jdbc.query("SELECT id FROM tb_shop WHERE id > ? ORDER BY id LIMIT ?",
                            (row, number) -> row.getLong(1), cursor, batchSize);
                    if (ids.isEmpty()) break;
                    Boolean appended = withState(wait, ignored -> {
                        if (!generation.equals(prepare())) throw unavailable("加载期间过滤器世代改变", null);
                        filter().add(ids);
                        return true;
                    });
                    if (appended == null) throw unavailable("加载批次等待超时", null);
                    cursor = ids.get(ids.size() - 1);
                }
                Boolean completed = withState(wait, ignored -> {
                    if (!generation.equals(prepare()) || !load.isHeldByCurrentThread())
                        throw unavailable("加载期间锁或过滤器世代失效", null);
                    long published = script("""
                            if redis.call('hget',KEYS[1],'guardGeneration') == ARGV[1]
                              and redis.call('getrange',KEYS[2],
                                math.ceil(tonumber(redis.call('hget',KEYS[1],'size'))/8),
                                math.ceil(tonumber(redis.call('hget',KEYS[1],'size'))/8)+35) == ARGV[1]
                              and redis.call('hexists',KEYS[4],ARGV[2]) == 1
                              and redis.call('hexists',KEYS[5],ARGV[2]) == 1 then
                                redis.call('set',KEYS[3],ARGV[1]); return 1;
                            end; return 0;
                            """, List.of(CONFIG, FILTER, READY, LOAD_LOCK, STATE_LOCK), generation,
                            redisson.getId() + ":" + Thread.currentThread().threadId());
                    if (published != 1) throw unavailable("完整加载完成标记发布失败", null);
                    return true;
                });
                if (completed == null) throw unavailable("完整加载完成标记等待超时", null);
                startupLoaded = true;
            } finally {
                if (load.isHeldByCurrentThread()) load.unlock();
            }
        } catch (RuntimeException failure) {
            log.warn("店铺过滤器完整加载失败，保持放行并等待下轮重试", failure);
        }
    }

    /** 配置变化不能套用旧位图；读取侧放行，写入侧明确失败以免误写旧格式。 */
    private RBloomFilter<Long> filter() {
        return redisson.getBloomFilter(FILTER, org.redisson.client.codec.LongCodec.INSTANCE);
    }

    /**
     * 在位图哈希区域之后嵌入世代标记，不改变 Redisson 的 ID 编码与哈希。
     * SDK 在准备后遇到位图丢失可能自行重建位图；尾部标记不会被 SDK 重建，因而仍能识别数据丢失。
     * 这里只补齐标记、不清空已有位，完整扫描成功前始终保持放行。
     */
    private String prepare() {
        RBloomFilter<Long> bloom = filter();
        bloom.tryInit(capacity, probability);
        if (bloom.getExpectedInsertions() != capacity || Double.compare(bloom.getFalseProbability(), probability) != 0)
            throw unavailable("过滤器容量配置变化，需要另行迁移", null);
        String next = UUID.randomUUID().toString();
        return redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, """
                local epoch=redis.call('hget',KEYS[1],'guardGeneration');
                local size=tonumber(redis.call('hget',KEYS[1],'size'));
                local offset=math.ceil(size/8);
                local marker=redis.call('getrange',KEYS[2],offset,offset+35);
                if not epoch or marker ~= epoch then
                    redis.call('del',KEYS[3]);
                    epoch=ARGV[1]; redis.call('hset',KEYS[1],'guardGeneration',epoch);
                    redis.call('setrange',KEYS[2],offset,epoch);
                end;
                return epoch;
                """, RScript.ReturnType.VALUE, List.of(CONFIG, FILTER, READY), next);
    }

    /** 同时核对配置、位图和就绪世代，任何缺失都清除旧就绪状态。 */
    private String readyGeneration() {
        return redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, """
                local ready=redis.call('get',KEYS[3]);
                local epoch=redis.call('hget',KEYS[1],'guardGeneration');
                local size=tonumber(redis.call('hget',KEYS[1],'size'));
                local hashes=tonumber(redis.call('hget',KEYS[1],'hashIterations'));
                if ready and epoch == ready and size and hashes then
                    local offset=math.ceil(size/8);
                    if redis.call('getrange',KEYS[2],offset,offset+35) == ready then return ready end;
                end;
                redis.call('del',KEYS[3]); return nil;
                """, RScript.ReturnType.VALUE, List.of(CONFIG, FILTER, READY));
    }

    /** 短期状态互斥阻止内部新增重建位图后继续使用旧 ready。 */
    private <T> T withState(Duration timeout, Function<Void, T> action) {
        RLock lock = redisson.getLock(STATE_LOCK);
        try {
            if (!lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) return null;
            try {
                T result = action.apply(null);
                if (!lock.isHeldByCurrentThread()) throw unavailable("过滤器状态锁失效", null);
                return result;
            }
            finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable("过滤器状态锁等待被中断", interrupted);
        }
    }

    /** 原子核对 RLock 的 Redisson 3.52 所有权字段与数据写入，消除检查后失锁窗口。 */
    private void owned(Long id, String body, List<Object> keys, String... values) {
        Object[] args = new Object[values.length + 1];
        args[0] = redisson.getId() + ":" + Thread.currentThread().threadId();
        System.arraycopy(values, 0, args, 1, values.length);
        try {
            long done = script("if redis.call('hexists',KEYS[1],ARGV[1]) == 0 then return 0 end; " + body + " return 1;", keys, args);
            if (done != 1) throw unavailable("店铺锁已失效，拒绝发布缓存", null);
        } catch (RuntimeException failure) {
            throw unavailable("店铺缓存写入协调失败", failure);
        }
    }

    /** 以字符串编码执行原子协调脚本。 */
    private long script(String lua, List<Object> keys, Object... arguments) {
        return redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, lua, RScript.ReturnType.INTEGER, keys, arguments);
    }

    /** 两个入口与写操作必须使用完全相同的店铺锁键。 */
    private static String lockKey(Long id) { return "lock:shop:" + id; }
    /** 基础店铺缓存键，兼容原有空值标记。 */
    private static String basicKey(Long id) { return "cache:shop:" + id; }
    /** 商详公共快照缓存键。 */
    private static String detailKey(Long id) { return "cache:shop-detail:" + id; }
    /** 两个详情入口共享的不存在标记键。 */
    private static String absentKey(Long id) { return "cache:shop:not-found:" + id; }
    /** 创建可让写事务回滚的技术异常。 */
    private static ShopCacheAccessException unavailable(String message, Throwable cause) {
        return new ShopCacheAccessException(message, cause);
    }
}
