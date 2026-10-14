package com.dianping.controller;

import com.dianping.dto.RefreshTokenDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerContractTest {

    @Test
    void refreshEndpointUsesExpectedPostMapping() throws Exception {
        Method method = UserController.class.getDeclaredMethod("refresh", RefreshTokenDTO.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/refresh");
    }

    @Test
    void logoutAcceptsRefreshTokenSoItCanBeRevoked() throws Exception {
        Method method = UserController.class.getDeclaredMethod("logout", RefreshTokenDTO.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/logout");
    }
}
