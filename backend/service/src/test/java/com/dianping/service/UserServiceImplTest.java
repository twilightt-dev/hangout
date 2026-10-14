package com.dianping.service;

import com.dianping.VO.TokenVO;
import com.dianping.constant.RedisConstants;
import com.dianping.constant.SystemConstants;
import com.dianping.dto.LoginDTO;
import com.dianping.entity.User;
import com.dianping.result.Result;
import com.dianping.security.TokenService;
import com.dianping.service.impl.UserServiceImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private TokenService tokenService;
    private UserServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        tokenService = mock(TokenService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(RedisConstants.LOGIN_CODE_KEY + "19112345678")).thenReturn("123456");
        User user = new User().setId(42L).setPhone("19112345678");
        UserInfoService userInfoService = mock(UserInfoService.class);
        service = spy(new UserServiceImpl(redisTemplate, tokenService, passwordEncoder, userInfoService));
        doReturn(user).when(service).getOne(any(Wrapper.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void verificationCodeCanOnlyBeConsumedOnce() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);
        TokenVO tokens = new TokenVO("access", "refresh", "Bearer", 900L);
        when(tokenService.issue("42")).thenReturn(tokens);
        LoginDTO request = new LoginDTO();
        request.setPhone("19112345678");
        request.setLoginType(SystemConstants.CODE_LOGIN);
        request.setVerifyCode("123456");

        Result<TokenVO> first = service.login(request);
        Result<TokenVO> duplicate = service.login(request);

        assertThat(first.getCode()).as(first.getMsg()).isEqualTo(1);
        assertThat(first.getData()).isSameAs(tokens);
        assertThat(duplicate.getCode()).isEqualTo(0);
        assertThat(duplicate.getMsg()).isEqualTo("验证码已失效");
        verify(tokenService).issue("42");
    }

    @Test
    void loginCodeSendingIsRateLimitedPerPhone() {
        when(values.setIfAbsent(
                RedisConstants.CODE_SEND_COOLDOWN_KEY + "login:19112345678",
                "1",
                RedisConstants.CODE_SEND_COOLDOWN_TTL,
                java.util.concurrent.TimeUnit.SECONDS
        )).thenReturn(false);

        Result<Void> result = service.sendCodeWhenLogin("19112345678");

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getMsg()).contains("过于频繁");
    }
}
