package com.dianping.security;

import com.dianping.VO.TokenVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {

    private JwtUtils jwtUtils;
    private ValueOperations<String, String> values;
    private TokenService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtUtils = mock(JwtUtils.class);
        JwtProperties properties = new JwtProperties();
        properties.setAccessTtl(Duration.ofMinutes(15));
        properties.setRefreshTtl(Duration.ofDays(7));
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        service = new TokenService(jwtUtils, properties, redisTemplate);
    }

    @Test
    void refreshConsumesOldRegistrationAndRegistersRotatedToken() {
        Claims oldClaims = mock(Claims.class);
        when(oldClaims.getId()).thenReturn("old-jti");
        when(oldClaims.getSubject()).thenReturn("42");
        when(jwtUtils.parseRefreshToken("old-refresh")).thenReturn(oldClaims);
        when(values.getAndDelete("auth:refresh:old-jti")).thenReturn("42");

        Claims newClaims = mock(Claims.class);
        when(newClaims.getId()).thenReturn("new-jti");
        when(jwtUtils.generateAccessToken("42")).thenReturn("new-access");
        when(jwtUtils.generateRefreshToken("42")).thenReturn("new-refresh");
        when(jwtUtils.parseRefreshToken("new-refresh")).thenReturn(newClaims);

        TokenVO refreshed = service.refresh("old-refresh");

        assertThat(refreshed.getAccessToken()).isEqualTo("new-access");
        assertThat(refreshed.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(refreshed.getAccessTtl()).isEqualTo(900L);
        verify(values).set("auth:refresh:new-jti", "42", Duration.ofDays(7));
    }

    @Test
    void alreadyConsumedRefreshTokenCannotBeReplayed() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("used-jti");
        when(jwtUtils.parseRefreshToken("used-refresh")).thenReturn(claims);
        when(values.getAndDelete("auth:refresh:used-jti")).thenReturn(null);

        assertThatThrownBy(() -> service.refresh("used-refresh"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("已失效");
    }
}
