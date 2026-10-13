package com.dianping.security;

import com.dianping.VO.TokenVO;
import com.dianping.config.SecurityConfig;
import com.dianping.controller.UserController;
import com.dianping.filter.JwtAuthenticationFilter;
import com.dianping.result.Result;
import com.dianping.service.UserInfoService;
import com.dianping.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@ContextConfiguration(classes = {UserController.class, SecurityConfig.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserInfoService userInfoService;

    @MockitoBean
    private TokenService tokenService;

    @BeforeEach
    void letJwtFilterContinueTheChain() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        when(userService.sendCodeWhenLogin(any())).thenReturn(Result.success());
        when(userService.sendCodeWhenRegister(any())).thenReturn(Result.success());
        when(userService.login(any())).thenReturn(Result.success(
                new TokenVO("access", "refresh", "Bearer", 900L)));
        when(userService.register(any())).thenReturn(Result.success());
        when(tokenService.refresh(any())).thenReturn(
                new TokenVO("access", "refresh", "Bearer", 900L));
    }

    @Test
    void allAuthenticationEntryPointsArePublic() throws Exception {
        mvc.perform(post("/user/login/code").param("phone", "19112345678"))
                .andExpect(status().isOk());
        mvc.perform(post("/user/register/code").param("phone", "19112345678"))
                .andExpect(status().isOk());
        mvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"19112345678","verifyCode":"123456",
                                 "password":"Test123456","confirmPassword":"Test123456"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"19112345678","loginType":"password",
                                 "password":"Test123456"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/user/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/user/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void currentUserEndpointStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized());
    }
}
