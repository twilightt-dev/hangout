package com.dianping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.constant.RedisConstants;
import com.dianping.dto.UserDTO;
import com.dianping.entity.SeckillVoucher;
import com.dianping.entity.Voucher;
import com.dianping.entity.VoucherOrder;
import com.dianping.mapper.VoucherMapper;
import com.dianping.mapper.VoucherOrderMapper;
import com.dianping.result.Result;
import com.dianping.service.SeckillVoucherService;
import com.dianping.service.VoucherOrderPersistService;
import com.dianping.service.VoucherOrderService;
import com.dianping.utils.RedisIdWorker;
import com.dianping.utils.SimpleRedisLock;
import com.dianping.utils.UserHolder;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;



@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private VoucherOrderPersistService voucherOrderPersistService;

    private static final String ORDER_GROUP = "g1";
    private static final int CLAIM_BATCH_SIZE = 10;
    private static final int MAX_CLAIM_PAGES = 5;
    // 实例使用独立身份；重启后由 XAUTOCLAIM 恢复旧实例的超时 Pending。
    private final String consumerName = "orders-" + UUID.randomUUID();
    private String pendingCursor = "0-0";

    @Value("${seckill.stream.claim-min-idle-ms:60000}")
    private long claimMinIdleMillis = 60_000;
    @Value("${seckill.stream.recovery-interval-ms:10000}")
    private long recoveryIntervalMillis = 10_000;

    // Redis 6.2+；通过脚本接口调用命令，沿用 StringRedisTemplate 的嵌套结果反序列化。
    private static final DefaultRedisScript<List> CLAIM_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('XAUTOCLAIM', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], 'COUNT', ARGV[5])",
            List.class);


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT ;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua")) ;
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //异步处理线程池   创立了一个单线程线程池，专门用于处理秒杀订单
    //订单按顺序处理，同一个JVM内不会有多个线程同时写订单 但数据库落库串行执行，吞吐量受单个消费者限制
    // 由当前 Service 实例负责启动和关闭
    private final ExecutorService orderExecutor =
            Executors.newSingleThreadExecutor();
    // volatile 保证后台线程能及时看到停止信号
    private volatile boolean running = true;


    //在类初始化之后执行
    @PostConstruct
    private void init() {
        if (claimMinIdleMillis <= 0 || recoveryIntervalMillis <= 0) {
            throw new IllegalArgumentException("Stream 接管空闲时间和扫描间隔必须大于 0");
        }
    //向线程池提交一个长期运行的消费者任务
        orderExecutor.submit(new VoucherOrderHandler());
    }

    @PreDestroy
    public void destroy() {
        // 通知消费者：当前这条处理结束后，不再开始下一轮
        running = false;

        // 不再接收新任务，但不会中断正在执行的任务
        orderExecutor.shutdown();

        try {
            // 给当前订单处理、事务提交和 ACK 留出时间
            if (!orderExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("订单消费者未在规定时间内退出，请求中断");

                // 向正在运行的任务发出中断请求
                orderExecutor.shutdownNow();

                if (!orderExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("订单消费者仍未退出，请检查 Redis 或数据库调用超时");
                }
            }
        } catch (InterruptedException e) {
            orderExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldRun() {
        return running && !Thread.currentThread().isInterrupted();
    }

    // 新消息与超时 Pending 共用消费线程，避免恢复任务与自身并发落库。
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            long lastRecoveryNanos = 0;
            boolean firstPass = true;
            while (shouldRun()) {
                try {
                    // 启动即扫描，之后即使没有新消息或异常也定期扫描。
                    long now = System.nanoTime();
                    if (firstPass || now - lastRecoveryNanos >= TimeUnit.MILLISECONDS.toNanos(recoveryIntervalMillis)) {
                        firstPass = false;
                        lastRecoveryNanos = now;
                        try {
                            handlePendingList();
                        } catch (Exception e) {
                            if (!shouldRun()) {
                                return;
                            }
                            log.error("接管 Pending 失败，下个扫描周期重试", e);
                        }
                    }
                    if (!shouldRun()) {
                        return;
                    }
                    //读取消息队列中的订单信息
                    List<MapRecord<String , Object , Object>> list = stringRedisTemplate
                            .opsForStream().read(
                                    Consumer.from(ORDER_GROUP, consumerName),
                                    StreamReadOptions.empty()
                                            .count(1).block(Duration.ofSeconds(2)) ,
                                    StreamOffset.create(RedisConstants.SECKILL_STREAM, ReadOffset.lastConsumed())
                            ) ;
                    //判断订单消息是否为空
                    if(list == null || list.isEmpty()){
                        continue ;//直接开始下一次读取
                    }
                    MapRecord<String , Object , Object> record = list.get(0) ;
                    processOrder(record.getId(), record.getValue());
                } catch (Exception e) {
                    // 如果正在关闭，就结束任务，不再启动重试
                    if (!shouldRun()) {
                        return;
                    }

                    log.error("读取或处理订单异常，未确认消息将由定期扫描恢复", e);
                    if (!pauseAfterFailure()) {
                        return;
                    }
                }
            }
        }
    }

    // 接管组内任意消费者（包括自己）长时间未确认的消息。
    // 限制每轮页数并保留游标，避免大量 Pending 或坏消息饿死新订单。
    private void handlePendingList() {
        for (int page = 0; page < MAX_CLAIM_PAGES && shouldRun(); page++) {
            List<?> response = stringRedisTemplate.execute(
                    CLAIM_SCRIPT, List.of(RedisConstants.SECKILL_STREAM),
                    ORDER_GROUP, consumerName, String.valueOf(claimMinIdleMillis),
                    pendingCursor, String.valueOf(CLAIM_BATCH_SIZE));
            if (response == null || response.size() < 2) {
                throw new IllegalStateException("XAUTOCLAIM 未返回有效扫描结果");
            }
            pendingCursor = response.get(0).toString();
            List<?> entries = (List<?>) response.get(1);
            for (Object entry : entries) {
                if (!shouldRun()) {
                    // 已接管但未处理的消息仍在 PEL，后续实例可再次接管。
                    return;
                }
                List<?> message = (List<?>) entry;
                RecordId messageId = RecordId.of(message.get(0).toString());
                try {
                    List<?> fields = (List<?>) message.get(1);
                    Map<Object, Object> value = new LinkedHashMap<>();
                    for (int i = 0; i < fields.size(); i += 2) {
                        value.put(fields.get(i), fields.get(i + 1));
                    }
                    processOrder(messageId, value);
                } catch (Exception e) {
                    if (!shouldRun()) {
                        return;
                    }
                    // 不 ACK，达到空闲阈值后再试；本轮继续处理后续消息。
                    log.error("接管订单处理失败，messageId=" + messageId, e);
                }
            }
            // Redis 7+ 会额外返回已被删除/裁剪、无法恢复正文的消息 ID。
            if (response.size() > 2 && response.get(2) instanceof List<?> deleted && !deleted.isEmpty()) {
                log.warn("Pending 对应的 Stream 消息已被删除，需要核对订单: " + deleted);
            }
            if ("0-0".equals(pendingCursor)) {
                return;
            }
            // 即使 entries 为空，游标非 0-0 也不能提前结束扫描。
        }
    }

    private void processOrder(RecordId messageId, Map<Object, Object> value) {
        VoucherOrder order = new VoucherOrder();
        order.setId(readOrderField(value, "orderId"));
        order.setUserId(readOrderField(value, "userId"));
        order.setVoucherId(readOrderField(value, "voucherId"));
        // 独立 Service 返回时事务已经提交，之后才确认消息。
        voucherOrderPersistService.createVoucherOrder(order);
        stringRedisTemplate.opsForStream().acknowledge(
                RedisConstants.SECKILL_STREAM, ORDER_GROUP, messageId);
    }

    private Long readOrderField(Map<Object, Object> value, String field) {
        Object raw = value.get(field);
        if (raw == null) {
            throw new IllegalArgumentException("订单消息缺少字段: " + field);
        }
        return Long.valueOf(raw.toString());
    }

    private boolean pauseAfterFailure() {
        try {
            Thread.sleep(1000);
            return shouldRun();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

        @Override
        public Result<Long> seckillVoucher(Long voucherId) {
            //用户id
            Long userId = UserHolder.getUser().getId();
            //订单id
            Long orderId = redisIdWorker.nextId("order");
            // 执行lua脚本
            Long result = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Arrays.asList(RedisConstants.SECKILL_STOCK_KEY+voucherId,
                            RedisConstants.SECKILL_USER_KEY+voucherId,
                            RedisConstants.SECKILL_STREAM),
                    userId.toString() , voucherId.toString() , orderId.toString()
            );
            int r = result.intValue();
            // 判断结果是否为0
            if (r != 0) {
                // 不为0 ，代表没有购买资格
                return Result.error(r == 1 ? "库存不足" : "不能重复下单");
            }
            //返回订单id
            return Result.success(orderId);
        }







    }


