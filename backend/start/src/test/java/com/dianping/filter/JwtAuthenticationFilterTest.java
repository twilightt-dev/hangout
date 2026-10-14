package com.dianping.filter;

import com.dianping.dto.UserDTO;
import com.dianping.entity.User;
import com.dianping.mapper.UserMapper;
import com.dianping.security.JwtUtils;
import com.dianping.utils.UserHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        UserHolder.removeUser();
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoresBusinessUserForTheRequestAndClearsThreadLocalAfterwards() throws Exception {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        UserMapper userMapper = mock(UserMapper.class);
        Claims claims = mock(Claims.class);
        when(jwtUtils.parseAccessToken("access-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("42");
        when(userMapper.selectById(42L)).thenReturn(new User()
                .setId(42L)
                .setNickName("星球用户")
                .setIcon("/icons/42.png"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtils, userMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            UserDTO current = UserHolder.getUser();
            assertThat(current).isNotNull();
            assertThat(current.getId()).isEqualTo(42L);
            assertThat(current.getNickName()).isEqualTo("星球用户");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isSameAs(current);
        };

        filter.doFilter(request, response, chain);

        assertThat(UserHolder.getUser()).isNull();
    }
}
