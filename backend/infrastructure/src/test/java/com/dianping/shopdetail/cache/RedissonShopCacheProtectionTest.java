package com.dianping.shopdetail.cache;

import com.dianping.shop.cache.ShopCacheAccessException;
import com.dianping.shop.cache.ShopCacheProtection;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 通过共享适配器的公共端口验证故障放行、锁所有权和发布失败；替换外部 Redis SDK。 */
class RedissonShopCacheProtectionTest {
    private RedissonClient client;
    private RScript script;
    private RLock lock;
    private RedissonShopCacheProtection protection;

    /** 构建隔离的 Redis 外部边界，测试不连接业务数据库。 */
    @BeforeEach void setup() throws Exception {
        client = mock(RedissonClient.class);
        script = mock(RScript.class);
        lock = mock(RLock.class);
        when(client.getLock(anyString())).thenReturn(lock);
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(client.getId()).thenReturn("test-client");
        when(lock.tryLock(anyLong(), any(java.util.concurrent.TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        protection = new RedissonShopCacheProtection(client, mock(JdbcTemplate.class), 100000, 0.001, 1000, 2000, 120000);
    }

    /** 就绪状态缺失时必须放行，不能把空过滤器解释成数据库没有店铺。 */
    @Test void missingReadinessAllowsQuery() {
        assertThat(protection.mayExist(7L)).isTrue();
    }

    /** Redis 连接异常必须放行读取。 */
    @Test void connectionFailureAllowsQuery() throws Exception {
        when(lock.tryLock(anyLong(), any(java.util.concurrent.TimeUnit.class))).thenThrow(new IllegalStateException("offline"));
        assertThat(protection.mayExist(7L)).isTrue();
    }

    /** 看门狗模式有界取锁，正常释放不得使用强制解锁。 */
    @Test void leaseUsesWatchdogAndClosesOnlyOwnedLock() throws Exception {
        try (ShopCacheProtection.Lease lease = protection.tryLock(7L, Duration.ofMillis(25))) {
            assertThat(lease).isNotNull();
            lease.verifyOwnership();
        }
        verify(lock).tryLock(25L, java.util.concurrent.TimeUnit.MILLISECONDS);
        verify(lock).unlock();
        verify(lock, never()).forceUnlock();
    }

    /** 丢锁后不得释放新所有者的锁。 */
    @Test void lostLeaseCannotUnlockNewOwner() {
        var lease = protection.tryLock(7L, Duration.ZERO);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        assertThatThrownBy(lease::verifyOwnership).isInstanceOf(ShopCacheAccessException.class);
        lease.close();
        verify(lock, never()).unlock();
    }

    /** Redis 原子校验拒绝发布时向调用方明确失败，不能静默成功。 */
    @Test void rejectedAtomicPublicationFails() {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(Object[].class)))
                .thenReturn(0L);
        assertThatThrownBy(() -> protection.publish(7L, "cache:shop:7", "{}", Duration.ofMinutes(1)))
                .isInstanceOf(ShopCacheAccessException.class);
    }

    /** 参数不合法应在装配时失败，避免零容量或永久空值缓存。 */
    @Test void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new RedissonShopCacheProtection(client, mock(JdbcTemplate.class), 0, 0.001, 1, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
