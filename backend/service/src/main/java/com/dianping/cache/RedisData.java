package com.dianping.cache;

import lombok.Data;

import java.time.Instant;

@Data
public class RedisData<T>{
    private T data ;
    //业务的逻辑上过期时间
    private Instant expireAt ;
}
