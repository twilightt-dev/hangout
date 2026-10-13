package com.dianping.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtils {

    private static final String TOKEN_TYPE = "token_type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey secretKey;
    private final JwtProperties properties;
    //依旧构造器注入
    public JwtUtils(JwtProperties properties) {
        if (properties.getSecret() == null
                || properties.getSecret().isBlank()) {
            throw new IllegalArgumentException(
                    "security.jwt.secret must not be blank"
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64URL.decode(properties.getSecret())
        );
        this.properties = properties;
    }

    public String generateAccessToken(String subject) {
        return generateToken(
                subject,
                ACCESS,
                properties.getAccessTtl()
        );
    }

    public String generateRefreshToken(String subject) {
        return generateToken(
                subject,
                REFRESH,
                properties.getRefreshTtl()
        );
    }

    private String generateToken(
            String subject,
            String tokenType,
            Duration ttl) {

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(secretKey)
                .compact();
    }
    //Claims就是JJWT将JWT的payload解析后得到的java对象，用于读取sub、jti、ttl和自定义字段等，本质上类似Map<String , Object>
    public Claims parseAccessToken(String token) {
        return parseToken(token, ACCESS);
    }

    public Claims parseRefreshToken(String token) {
        return parseToken(token, REFRESH);
    }

    private Claims parseToken(
            String token,
            String expectedType) {

        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String actualType =
                claims.get(TOKEN_TYPE, String.class);

        if (!expectedType.equals(actualType)) {
            throw new JwtException("Token 类型不正确");
        }

        return claims;
    }
}
