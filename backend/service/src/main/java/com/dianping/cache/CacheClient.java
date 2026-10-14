package com.dianping.cache;

import com.dianping.constant.RedisConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class CacheClient {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    /**
     * 查询缓存
     * @param key
     * @param type
     * @return
     * @param <T>
     */
    public <T>CacheResult<T> get(String key , Class<T> type){
        final String json ;
        try {
            json = stringRedisTemplate
                    .opsForValue()
                    .get(key);
        } catch (DataAccessException e) {
            // Redis 不可用时，按缓存未命中处理，允许业务回源数据库
            log.warn("查询缓存失败，key={}", key, e);
            return CacheResult.miss();
        }

        if(json == null){
            return CacheResult.miss() ;
        }

        if(Objects.equals(json , RedisConstants.NOT_FOUND)){
            return CacheResult.notFound() ;
        }

        if (!StringUtils.hasText(json)) {
            log.warn("缓存值为空白串，按脏数据删除，key={}", key);
            deleteSafely(key);
            return CacheResult.miss();
        }
        try {
            T value = objectMapper.readValue(json, type);
            return CacheResult.hit(value);
        } catch (JsonProcessingException e) {
            log.warn("缓存反序列化失败，删除缓存，key={}", key, e);
            deleteSafely(key);
            return CacheResult.miss();
        }

    }

    /**
     * 写入正常对象缓存，并添加ttl抖动
     */
    public boolean set(String key , Object value , Duration ttl){
        try{
            String json = objectMapper.writeValueAsString(value) ;
            long offset = ThreadLocalRandom.current().nextLong(-180, 181);
            stringRedisTemplate.opsForValue().set(key , json , ttl.plusSeconds(offset)) ;
            return true;
        } catch (JsonProcessingException e) {
            log.warn("缓存序列化失败, key = {}" ,key ,e);
            return false ;
        }catch(DataAccessException e){
            log.warn("缓存写入失败,key = {}" ,key ,e);
            return false ;
        }
    }

    /**
     * 写入“数据不存在”标记处理缓存穿透
     */
    public boolean setNotFound(String key , Duration ttl){
        try{
            stringRedisTemplate.opsForValue().set(key , RedisConstants.NOT_FOUND , ttl) ;
            return true ;
        }catch(DataAccessException e){
            log.warn("空值缓存写入失败:{}" , key , e);
            return false ;
        }
    }


    public void deleteSafely(String key){
        try {
            stringRedisTemplate.delete(key);
        }catch(DataAccessException e){
            log.warn("缓存删除失败:{}" , key , e) ;
        }
    }

    //采用逻辑过期方案时写入逻辑缓存
    public void setWithLogicalExpire(String key , Object value , Duration logicalTtl
    ,Duration physicalTtl){
         RedisData<Object> redisData = new RedisData<>() ;
         redisData.setData(value) ;
         redisData.setExpireAt(Instant.now().plus(logicalTtl)) ;
         try{
             String json = objectMapper.writeValueAsString(redisData) ;
             stringRedisTemplate.opsForValue().set(key , json , physicalTtl) ;
         } catch (JsonProcessingException e) {
             log.warn("逻辑缓存序列化失败: key={}" , key , e ) ;
         } catch(DataAccessException e){
             log.warn("逻辑缓存写入redis失败，key = {}" , key , e) ;
         }
    }

    //读取逻辑缓存
    public <T> RedisData<T> getWithLogicalExpire(String key , Class<T> type){
        String json = stringRedisTemplate.opsForValue().get(key);

        if(!StringUtils.hasText(json)){
            return null ;
        }

        try {
            JavaType javaType = objectMapper.getTypeFactory()
                    .constructParametricType(RedisData.class, type);

            return objectMapper.readValue(json, javaType);

        } catch (JsonProcessingException e) {
            log.warn("逻辑缓存反序列化失败，删除缓存，key={}", key, e);
            deleteSafely(key);
            return null;
        }

    }


}
