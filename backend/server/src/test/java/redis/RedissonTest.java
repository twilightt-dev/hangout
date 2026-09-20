package redis;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

public class RedissonTest {

    @Resource
    private RedissonClient redissonClient ;

    @Test
    void testRedisson() throws Exception{
        RLock lock = redissonClient.getLock("anyLock") ;
        boolean getLock = lock.tryLock(1 , 10 , TimeUnit.SECONDS) ;

        if(getLock){
            try{
                System.out.println("执行业务");
            }
            finally {
                lock.unlock() ;
            }
        }
    }
}
