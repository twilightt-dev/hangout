package com.dianping.utils;

public interface ILock {

    //获取锁，timeout是锁的超时时间，过期后自动释放
    boolean tryLock(long timeout) ;

    //释放锁
    void unlock() ;
}
