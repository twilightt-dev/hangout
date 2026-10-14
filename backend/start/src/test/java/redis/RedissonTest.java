package redis;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// 仅显式连接隔离 Redis，不能使用应用的生产连接。
@EnabledIfSystemProperty(named = "legacy.redis.port", matches = "[0-9]+")
@SpringJUnitConfig(RedissonTest.TestConfig.class)
public class RedissonTest {

    @Resource
    private RedissonClient redissonClient ;

    @Test
    void testRedisson() throws Exception{
        RLock lock = redissonClient.getLock("test:lock:" + UUID.randomUUID()) ;
        boolean getLock = lock.tryLock(1 , 10 , TimeUnit.SECONDS) ;
        assertTrue(getLock, "应成功获得隔离测试锁");

        if(getLock){
            try{
                assertTrue(lock.isHeldByCurrentThread(), "锁应归当前线程持有");
            }
            finally {
                lock.unlock() ;
            }
        }
        assertFalse(lock.isHeldByCurrentThread(), "释放后当前线程不应继续持锁");
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean(destroyMethod = "shutdown")
        RedissonClient redissonClient() {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://127.0.0.1:" + System.getProperty("legacy.redis.port"));
            return Redisson.create(config);
        }
    }
}
