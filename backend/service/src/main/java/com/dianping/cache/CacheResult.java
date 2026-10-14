package com.dianping.cache;

public record CacheResult<T>(
        CacheState state ,
        T value
) {
    //命中
    public static <T> CacheResult<T> hit(T value){
        return new CacheResult<T>(CacheState.HIT , value) ;
    }

    @Override
    public T value() {
        return value;
    }

    @Override
    public CacheState state() {
        return state;
    }

    //命中
    public static <T> CacheResult<T> miss(){
        return new CacheResult<T>(CacheState.MISS , null) ;
    }

    //缓存穿透数据
    public static <T> CacheResult<T> notFound(){
        return new CacheResult<T>(CacheState.NOT_FOUND , null) ;
    }
}
