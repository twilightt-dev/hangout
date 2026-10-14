package com.dianping.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private static final String KEY_PREFIX = "lock:" ;
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-" ;


    private final String name ; //业务资源的名称 比如order1000
    private final StringRedisTemplate stringRedisTemplate ;
    public SimpleRedisLock(String name , StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT ;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua")) ;
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(long timeout) {
        //获取线程表示
        String threadId = ID_PREFIX + Thread.currentThread().threadId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name ,
                threadId , timeout , TimeUnit.SECONDS) ;

        return Boolean.TRUE.equals(success) ;

    }



    public void unlock() {
        String key = KEY_PREFIX + name;
        String threadId = ID_PREFIX + Thread.currentThread().threadId();


        stringRedisTemplate.execute(
                UNLOCK_SCRIPT ,
                Collections.singletonList(key),
                threadId
        );
    }
}
