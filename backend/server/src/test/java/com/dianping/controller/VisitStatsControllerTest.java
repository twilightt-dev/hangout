package com.dianping.controller;

import com.dianping.config.SecurityConfig;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.filter.JwtAuthenticationFilter;
import com.dianping.mapper.BlogMapper;
import com.dianping.mapper.ShopMapper;
import com.dianping.service.impl.VisitStatsServiceImpl;
import com.dianping.entity.Blog;
import com.dianping.entity.Shop;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VisitStatsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, VisitStatsServiceImpl.class})
@ContextConfiguration(classes = {VisitStatsController.class, SecurityConfig.class,
        GlobalExceptionHandler.class, VisitStatsServiceImpl.class})
class VisitStatsControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtAuthenticationFilter filter;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean BlogMapper blogs;
    @MockitoBean ShopMapper shops;

    private static final String BODY = """
            {"visitorId":"f7c35878-e1cf-4c38-a2b1-107058c251ec",
             "eventId":"d1f6b360-b1bf-4f81-ae41-388f036c0440"}
            """;

    @BeforeEach
    void setup() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(filter).doFilter(any(), any(), any());
        when(blogs.selectById(8L)).thenReturn(new Blog());
        when(shops.selectById(8L)).thenReturn(new Shop());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(12L);
    }

    @Test
    void anonymousVisitorsCanReportBothMetricsAndReceiveLatestCount() throws Exception {
        for (String path : List.of("/blog/8/visit", "/shop/8/visit")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                    .andExpect(jsonPath("$.data.count").value(12));
        }
    }

    @Test
    void invalidIdentityAndResourceIdsCannotWriteStatistics() throws Exception {
        for (String body : List.of("{}", "null", BODY.replace("f7c35878", "invalid!"))) {
            mvc.perform(post("/blog/8/visit").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/shop/0/visit").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(redis);
    }

    @Test
    void missingResourcesCannotBeCounted() throws Exception {
        mvc.perform(post("/blog/99/visit").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mvc.perform(post("/shop/99/visit").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        verifyNoInteractions(redis);
    }

    @Test
    void unavailableRedisAndRateLimitReturnErrorsInsteadOfZero() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        mvc.perform(post("/blog/8/visit").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(0));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-1L);
        mvc.perform(post("/shop/8/visit").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(0));
    }
}
