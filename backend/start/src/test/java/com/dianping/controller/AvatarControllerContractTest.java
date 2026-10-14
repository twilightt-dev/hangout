package com.dianping.controller;

import com.dianping.dto.AvatarUpdateDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarControllerContractTest {

    @Test
    void exposesAuthenticatedAvatarUploadEndpoint() throws Exception {
        Method method = AvatarController.class.getDeclaredMethod("uploadAvatar", org.springframework.web.multipart.MultipartFile.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/upload/avatar");
    }

    @Test
    void exposesDefaultAvatarListEndpoint() throws Exception {
        Method method = AvatarController.class.getDeclaredMethod("defaultAvatars");
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/user/avatar/defaults");
    }

    @Test
    void exposesAvatarSaveEndpoint() throws Exception {
        Method method = AvatarController.class.getDeclaredMethod("updateAvatar", AvatarUpdateDTO.class);
        PutMapping mapping = method.getAnnotation(PutMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/user/avatar");
    }
}
