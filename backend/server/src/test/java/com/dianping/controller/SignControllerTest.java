package com.dianping.controller;

import com.dianping.config.SecurityConfig;
import com.dianping.filter.JwtAuthenticationFilter;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.service.SignService;
import com.dianping.VO.SignResultVO;
import com.dianping.VO.SignStatsVO;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SignController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ContextConfiguration(classes = {SignController.class, SecurityConfig.class, GlobalExceptionHandler.class})
class SignControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private SignService service;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void continueFilterChain() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void anonymousUsersCannotWriteOrReadSignIns() throws Exception {
        mvc.perform(post("/user/sign")).andExpect(status().isUnauthorized());
        mvc.perform(get("/user/sign/stats")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void signInNeedsNoClientIdentityOrDate() throws Exception {
        when(service.sign()).thenReturn(new SignResultVO("2026-10-09", true));
        mvc.perform(post("/user/sign").with(user("1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.date").value("2026-10-09"))
                .andExpect(jsonPath("$.data.alreadySigned").value(true));
    }

    @Test
    void statsExposeBothTotalsAndSignedDifference() throws Exception {
        when(service.stats()).thenReturn(new SignStatsVO("2026-10-09", "202610", false, 4, 0, 6, -2));
        mvc.perform(get("/user/sign/stats").with(user("1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todaySigned").value(false))
                .andExpect(jsonPath("$.data.monthlyDays").value(4))
                .andExpect(jsonPath("$.data.continuousDays").value(0))
                .andExpect(jsonPath("$.data.previousMonthDays").value(6))
                .andExpect(jsonPath("$.data.monthDifference").value(-2));
    }

    @Test
    void redisFailureReturnsServiceUnavailableInsteadOfZeroStats() throws Exception {
        when(service.stats()).thenThrow(new DataAccessResourceFailureException("offline"));
        mvc.perform(get("/user/sign/stats").with(user("1001")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(0));
    }
}
