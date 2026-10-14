package com.dianping.security;


import com.dianping.VO.TokenVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 签发双token
 * 将refreshtoken注册到redis
 * 刷新时轮换token
 * 退出时撤销refreshtoken
 */
@Slf4j
@Service
public class TokenService {
    //存redis的key名前缀
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;
    //构造器注入
    public TokenService(
            JwtUtils jwtUtils ,
            JwtProperties jwtProperties ,
            StringRedisTemplate stringRedisTemplate
    ){
        this.jwtUtils = jwtUtils;
        this.jwtProperties = jwtProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 签发token
     * @param subject 认证主题
     * @return token响应类
     */
    public TokenVO issue(String subject){

        String accessToken = jwtUtils.generateAccessToken(subject);
        String refreshToken = jwtUtils.generateRefreshToken(subject);

        Claims claims = jwtUtils.parseRefreshToken(refreshToken) ;
        stringRedisTemplate.opsForValue().set(REFRESH_KEY_PREFIX+claims.getId() ,
                subject , jwtProperties.getRefreshTtl()) ;

        return new TokenVO(accessToken , refreshToken , "Bearer" ,
                jwtProperties.getAccessTtl().toSeconds()) ;
    }


    /**
     * 刷新token
     * @param refreshToken 用于刷新的token
     * @return token响应类
     */
    public TokenVO refresh(String refreshToken){
        Claims claims = jwtUtils.parseRefreshToken(refreshToken) ;

        String key = REFRESH_KEY_PREFIX+claims.getId();
        String storedSubject = stringRedisTemplate.opsForValue().getAndDelete(key);

        if(storedSubject == null){
            throw new JwtException("Refresh token 已失效或已被使用");
        }

        if(!storedSubject.equals(claims.getSubject())){
            throw new JwtException("RefreshToken用户信息不匹配") ;
        }

        return issue(storedSubject) ;

    }

    /**
     * 撤销refreshToken，accessToken会过期可以不用撤销
     * @param refreshToken
     */
    public void revoke(String refreshToken){
        try{
            Claims claims = jwtUtils.parseRefreshToken(refreshToken) ;
            stringRedisTemplate.delete(REFRESH_KEY_PREFIX+claims.getId());
        }
        catch(JwtException | IllegalArgumentException ignored){
            log.debug("注销时RefreshToken已经失效") ;
        }
    }



}
