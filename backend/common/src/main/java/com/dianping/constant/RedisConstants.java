package com.dianping.constant;

import java.time.Duration;

public class RedisConstants {
    /**
     * 登录时redis里存验证码的key
     */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /**
     * 注册时redis里存验证码的key
     */
    public static final String REGISTER_CODE_KEY = "register:code:";
    /**
     * redis里存验证码的过期时间
     */
    public static final Long CODE_TTL = 2L;
    /**
     * 同一手机号、同一用途验证码的最短重发间隔。
     */
    public static final String CODE_SEND_COOLDOWN_KEY = "code:cooldown:";
    public static final Long CODE_SEND_COOLDOWN_TTL = 60L;

    //缓存穿透数据持续时间,2分钟
    public static final Duration CACHE_NULL_TTL = Duration.ofMinutes(2);
    //商铺缓存持续时间，30分钟
    public static final Duration SHOP_CACHE_TTL = Duration.ofMinutes(30);

    //商铺类型列表缓存持续时间，12小时
    public static final Duration SHOP_TYPE_LIST_TTL = Duration.ofHours(12);

    //商铺缓存key
    public static final String SHOP_CACHE_KEY = "cache:shop:";
    //商铺逻辑缓存key
    public static final String SHOP_LOGICAL_CACHE_KEY = "cache:shop:logic" ;
    //商铺类型列表缓存key
    public static final String SHOP_TYPE_LIST_KEY = "cache:shop-type:list";


    //空值标记
    public static final String NOT_FOUND = "not found" ;
    //互斥锁的key
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    //互斥锁的ttl，30秒，必须大于缓存重建时间
    public static final Long LOCK_SHOP_TTL = 30L;

    //记录秒杀券库存
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    //记录秒杀券对应的用户
    public static final String SECKILL_USER_KEY = "seckill:user:";
    //秒杀订单消息队列的名称
    public static final String SECKILL_STREAM = "seckill.orders" ;

    //订单落库时的锁对象
    public static final String ORDER_LOCK_KEY = "order:lock:";
    //订单的key
    public static final String VOUCHER_ORDER_KEY = "voucher:order:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String UPLOAD_OWNER_KEY = "upload:owner:";
    public static final Long UPLOAD_OWNER_TTL = 24L;
}
