package com.dianping.exception;

import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @Test
    void mapsRefreshAuthenticationFailureToUnauthorizedInsteadOfServerError() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/invalid-refresh").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("登录状态已失效，请重新登录"));
    }

    @Test
    void mapsInfrastructureFailureToServiceUnavailableWithoutLeakingDetails() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/data-access").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("服务暂时不可用，请稍后重试"));
    }

    @Test
    void mapsMissingRequestParameterToBadRequestResult() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/required-parameter").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("缺少请求参数：phone"));
    }

    @Test
    void mapsInvalidShopGeoQueryToBadRequestResult() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/invalid-geo").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("距离排序需要有效的经纬度"));
    }

    @Test
    void mapsInvalidQueryParameterTypeToBadRequestResult() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/typed-parameter")
                        .param("longitude", "not-a-number")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("请求参数格式错误：longitude"));
    }

    @Test
    void mapsBeanValidationFailureToBadRequestResult() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/test/validated-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("名称不能为空"));
    }

    @Test
    void mapsUnreadableJsonToBadRequestResult() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/test/request-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("请求体格式错误"));
    }

    @Test
    void mapsUnexpectedCheckedExceptionToInternalServerError() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/unexpected").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("服务器异常"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/invalid-refresh")
        void invalidRefresh() {
            throw new JwtException("expired");
        }

        @GetMapping("/test/data-access")
        void dataAccess() {
            throw new DataAccessResourceFailureException("redis://internal-host:6379");
        }

        @GetMapping("/test/required-parameter")
        void requiredParameter(@RequestParam String phone) {
        }

        @GetMapping("/test/invalid-geo")
        void invalidGeo() {
            throw new InvalidShopGeoQueryException();
        }

        @GetMapping("/test/typed-parameter")
        void typedParameter(@RequestParam Double longitude) {
        }

        @PostMapping("/test/request-body")
        void requestBody(@RequestBody Map<String, Object> body) {
        }

        @PostMapping("/test/validated-body")
        void validatedBody(@Valid @RequestBody TestRequest body) {
        }

        @GetMapping("/test/unexpected")
        void unexpected() throws Exception {
            throw new Exception("internal-only detail");
        }
    }

    record TestRequest(
            @NotBlank(message = "名称不能为空") String name) {
    }
}
