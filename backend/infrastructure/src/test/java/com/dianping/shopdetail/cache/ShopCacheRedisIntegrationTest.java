package com.dianping.shopdetail.cache;

import com.dianping.shared.cache.ShopCacheAccessException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

/** 显式开启后自行启动临时 Redis 容器，每例使用不同数据库，不访问业务 Redis。 */
@EnabledIfSystemProperty(named = "shop.cache.redis-test", matches = "true")
class ShopCacheRedisIntegrationTest {
    private static final AtomicInteger DATABASE = new AtomicInteger();
    private static final String FILTER = "{shop-bloom}:ids";
    private static String container;
    private static String address;
    private RedissonClient first;
    private RedissonClient second;
    private JdbcTemplate jdbc;
    private RedissonShopCacheProtection guard;
    private RedissonShopCacheProtection other;

    /** 由 Docker 随机分配端口，避免误用已有的业务容器或残留测试数据。 */
    @BeforeAll static void startRedis() throws Exception {
        container = docker("run", "--rm", "-d", "-p", "127.0.0.1::6379", "redis:latest",
                "redis-server", "--appendonly", "no");
        String mapping = docker("port", container, "6379/tcp");
        address = "redis://" + mapping.lines().findFirst().orElseThrow();
    }

    /** 只停止本测试刚创建的临时容器，Docker 自动回收其临时数据层。 */
    @AfterAll static void stopRedis() throws Exception {
        if (container != null) docker("stop", container);
    }

    /** 以参数数组调用 Docker，失败时提供完整诊断并限制等待时间。 */
    private static String docker(String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("docker");
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Docker 测试容器操作超时");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) throw new IllegalStateException(output);
        return output;
    }

    /** 两个独立客户端共用隔离 Redis 数据库和真实 H2 店铺表。 */
    @BeforeEach void setup() {
        Config config = new Config();
        config.useSingleServer().setAddress(address)
                .setDatabase(DATABASE.getAndIncrement()).setConnectionMinimumIdleSize(1).setConnectionPoolSize(4);
        first = Redisson.create(config);
        second = Redisson.create(config);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:bloom_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE tb_shop(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO tb_shop VALUES(10),(20)");
        guard = protection(first, jdbc);
        other = protection(second, jdbc);
    }

    /** 关闭两套测试连接；容器生命周期由测试启动方管理。 */
    @AfterEach void close() {
        if (second != null) second.shutdown();
        if (first != null) first.shutdown();
    }

    /** 仅完整加载后启用否定判断，多客户端共享过滤结果。 */
    @Test void completeLoadingEnablesSharedFiltering() {
        assertThat(guard.mayExist(99L)).isTrue();
        guard.recover();
        assertThat(other.mayExist(10L)).isTrue();
        assertThat(other.mayExist(99L)).isFalse();
    }

    /** 只写入新增 ID 不能把尚未完整扫描的数据标记为就绪。 */
    @Test void additionBeforeLoadingDoesNotEnableFiltering() {
        other.add(30L);
        assertThat(guard.mayExist(10L)).isTrue();
        assertThat(guard.mayExist(99L)).isTrue();
        guard.recover();
        assertThat(other.mayExist(30L)).isTrue();
        assertThat(other.mayExist(99L)).isFalse();
    }

    /** 位图单独丢失后即便马上新增，也必须重新全量加载才能拦截。 */
    @Test void lostBitmapAndConcurrentAdditionInvalidateReadiness() {
        guard.recover();
        first.getKeys().delete(FILTER);
        other.add(30L);
        assertThat(guard.mayExist(10L)).isTrue();
        assertThat(guard.mayExist(99L)).isTrue();
        guard.recover();
        assertThat(guard.mayExist(10L)).isTrue();
        assertThat(guard.mayExist(30L)).isTrue();
        assertThat(guard.mayExist(99L)).isFalse();
    }

    /** SDK 在状态检查后重建丢失位图，也不能沿用旧 READY 误拦截原有店铺。 */
    @Test void sdkRecreatedBitmapCannotReuseOldReadiness() {
        guard.recover();
        first.getKeys().delete(FILTER);
        first.<Long>getBloomFilter(FILTER, org.redisson.client.codec.LongCodec.INSTANCE).add(30L);
        assertThat(guard.mayExist(10L)).isTrue();
        assertThat(guard.mayExist(99L)).isTrue();
        guard.recover();
        assertThat(guard.mayExist(10L)).isTrue();
        assertThat(guard.mayExist(30L)).isTrue();
        assertThat(guard.mayExist(99L)).isFalse();
    }

    /** 配置缺失与剩余位图不构成可用过滤器，追加后同样需要完整恢复。 */
    @Test void lostConfigurationRequiresCompleteRecovery() {
        guard.recover();
        first.getKeys().delete(FILTER + ":config");
        assertThat(other.mayExist(99L)).isTrue();
        other.add(30L);
        assertThat(other.mayExist(99L)).isTrue();
        guard.recover();
        assertThat(other.mayExist(10L)).isTrue();
        assertThat(other.mayExist(99L)).isFalse();
    }

    /** 扫描失败保持放行，下一轮使用同一实例可以完成恢复。 */
    @Test void failedLoadingCanRetryWithoutFalseNegatives() {
        JdbcTemplate failing = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:missing_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        var recovering = protection(first, failing);
        recovering.recover();
        assertThat(recovering.mayExist(99L)).isTrue();
        failing.execute("CREATE TABLE tb_shop(id BIGINT PRIMARY KEY)");
        failing.update("INSERT INTO tb_shop VALUES(40)");
        recovering.recover();
        assertThat(recovering.mayExist(40L)).isTrue();
        assertThat(recovering.mayExist(99L)).isFalse();
    }

    /** 后台扫描与新增追加同一过滤器，扫描结果不会覆盖并发写入。 */
    @Test void concurrentAdditionSurvivesCompleteLoading() {
        CompletableFuture<Void> addition = CompletableFuture.runAsync(() -> other.add(1L));
        guard.recover();
        addition.join();
        assertThat(guard.mayExist(1L)).isTrue();
        assertThat(guard.mayExist(10L)).isTrue();
    }

    /** 空值标记共享、自动过期，正常发布原子清理旧标记。 */
    @Test void absenceIsSharedExpiresAndIsClearedOnPublication() throws Exception {
        try (var lease = guard.tryLock(7L, Duration.ZERO)) {
            assertThat(lease).isNotNull();
            guard.markNotFound(7L);
            assertThat(other.isNotFound(7L)).isTrue();
            guard.publish(7L, "cache:shop:7", "live", Duration.ofSeconds(10));
            assertThat(other.isNotFound(7L)).isFalse();
            assertThat(second.getBucket("cache:shop:7", StringCodec.INSTANCE).get()).isEqualTo("live");
            guard.markNotFound(7L);
        }
        Thread.sleep(250);
        assertThat(other.isNotFound(7L)).isFalse();
    }

    /** 两客户端同店互斥，不同店独立；后台实际工作线程独立取得并释放锁。 */
    @Test void clientsShareShopLockAndWorkerOwnsItsLease() {
        try (var lease = guard.tryLock(7L, Duration.ZERO)) {
            assertThat(lease).isNotNull();
            assertThat(other.tryLock(7L, Duration.ofMillis(20))).isNull();
            CompletableFuture.runAsync(() -> {
                try (var worker = other.tryLock(8L, Duration.ZERO)) {
                    assertThat(worker).isNotNull();
                    worker.verifyOwnership();
                    other.publish(8L, "cache:shop:8", "worker", Duration.ofSeconds(10));
                }
            }).join();
        }
        try (var next = other.tryLock(7L, Duration.ZERO)) { assertThat(next).isNotNull(); }
    }

    /** 锁失效的旧任务无法发布或误解锁新所有者，即使在同一个 Java 线程调用。 */
    @Test void staleOwnerCannotPublishAfterNewOwnerEvicts() throws Exception {
        var stale = guard.tryLock(7L, Duration.ZERO);
        first.getBucket("lock:shop:7").expire(Duration.ofMillis(1));
        Thread.sleep(30);
        try (var current = other.tryLock(7L, Duration.ZERO)) {
            assertThat(current).isNotNull();
            other.evict(7L);
            assertThatThrownBy(() -> guard.publish(7L, "cache:shop:7", "stale", Duration.ofSeconds(10)))
                    .isInstanceOf(ShopCacheAccessException.class);
            assertThatThrownBy(() -> guard.markNotFound(7L)).isInstanceOf(ShopCacheAccessException.class);
            stale.close();
            current.verifyOwnership();
            assertThat(second.getBucket("cache:shop:7").isExists()).isFalse();
            assertThat(other.isNotFound(7L)).isFalse();
        }
    }

    /** 测试使用小批次和短空值 TTL，使真实分批与过期行为可观察。 */
    private static RedissonShopCacheProtection protection(RedissonClient client, JdbcTemplate jdbc) {
        return new RedissonShopCacheProtection(client, jdbc, 100000, 0.001, 1, 1000, 150);
    }
}
