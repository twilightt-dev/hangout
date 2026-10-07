package com.dianping.service.impl;

import com.dianping.dto.AvatarUpdateDTO;
import com.dianping.dto.UserDTO;
import com.dianping.entity.User;
import com.dianping.result.Result;
import com.dianping.service.UserService;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.nio.file.Files;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AvatarServiceImplTest {

    @TempDir
    Path uploadDirectory;

    private UserService userService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private AvatarServiceImpl service;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new AvatarServiceImpl(userService, redis, uploadDirectory);
        UserDTO current = new UserDTO();
        current.setId(7L);
        UserHolder.saveUser(current);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void returnsEightStableDefaultAvatars() {
        List<?> defaults = service.defaultAvatars();

        assertThat(defaults).hasSize(8);
        assertThat(defaults).extracting("id")
                .containsExactly("default-01", "default-02", "default-03", "default-04",
                        "default-05", "default-06", "default-07", "default-08");
    }

    @Test
    void savesAValidDefaultAvatarForTheCurrentUser() {
        User existing = new User().setId(7L).setIcon("/imgs/avatars/7/old.jpg");
        when(userService.getById(7L)).thenReturn(existing);
        when(userService.updateById(any(User.class))).thenReturn(true);
        AvatarUpdateDTO request = new AvatarUpdateDTO()
                .setSourceType("DEFAULT")
                .setDefaultId("default-01");

        Result<?> result = service.updateAvatar(request);

        assertThat(result.getCode()).isEqualTo(1);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userService).updateById(captor.capture());
        assertThat(captor.getValue().getIcon()).isEqualTo("/imgs/icons/default-01.png");
    }

    @Test
    void rejectsUnknownDefaultAvatarWithoutUpdatingTheUser() {
        AvatarUpdateDTO request = new AvatarUpdateDTO()
                .setSourceType("DEFAULT")
                .setDefaultId("default-99");

        Result<?> result = service.updateAvatar(request);

        assertThat(result.getCode()).isZero();
        org.mockito.Mockito.verifyNoInteractions(userService);
    }

    @Test
    void rejectsARequestThatContainsTwoAvatarSources() {
        AvatarUpdateDTO request = new AvatarUpdateDTO()
                .setSourceType("DEFAULT")
                .setDefaultId("default-01")
                .setUploadPath("/imgs/avatars/tmp/7/file.jpg");

        Result<?> result = service.updateAvatar(request);

        assertThat(result.getCode()).isZero();
        org.mockito.Mockito.verifyNoInteractions(userService);
    }

    @Test
    void rejectsUnsupportedImageTypeBeforeWritingAFile() {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getOriginalFilename()).thenReturn("avatar.gif");

        Result<?> result = service.uploadAvatar(image);

        assertThat(result.getCode()).isZero();
        assertThat(uploadDirectory.resolve("avatars")).doesNotExist();
    }

    @Test
    void storesAValidUploadAsASquareTemporaryAvatar() throws Exception {
        BufferedImage source = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        MultipartFile image = new MockMultipartFile("image", "avatar.png", "image/png", bytes.toByteArray());

        Result<String> result = service.uploadAvatar(image);

        assertThat(result.getCode()).isEqualTo(1);
        String path = result.getData();
        assertThat(path).startsWith("/imgs/avatars/tmp/7/");
        BufferedImage stored = ImageIO.read(uploadDirectory.resolve(path.substring("/imgs/".length())).toFile());
        assertThat(stored.getWidth()).isEqualTo(stored.getHeight());
        verify(values).set(eq("avatar:upload:owner:avatars/tmp/7/" + path.substring(path.lastIndexOf('/') + 1)), eq("7"), eq(10L), eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    void promotesOnlyAnOwnedTemporaryAvatarAndRemovesThePreviousUpload() throws Exception {
        Path temporary = uploadDirectory.resolve("avatars/tmp/7/file.png");
        Files.createDirectories(temporary.getParent());
        Files.writeString(temporary, "avatar");
        when(values.get("avatar:upload:owner:avatars/tmp/7/file.png")).thenReturn("7");
        when(userService.getById(7L)).thenReturn(new User().setId(7L).setIcon("/imgs/avatars/7/old.png"));
        when(userService.updateById(any(User.class))).thenReturn(true);
        Path old = uploadDirectory.resolve("avatars/7/old.png");
        Files.createDirectories(old.getParent());
        Files.writeString(old, "old");

        Result<?> result = service.updateAvatar(new AvatarUpdateDTO()
                .setSourceType("UPLOAD")
                .setUploadPath("/imgs/avatars/tmp/7/file.png"));

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(uploadDirectory.resolve("avatars/7/file.png")).exists();
        assertThat(old).doesNotExist();
    }

    @Test
    void cleanupRemovesTemporaryFilesWithoutAnOwnerLease() throws Exception {
        Path temporary = uploadDirectory.resolve("avatars/tmp/7/expired.png");
        Files.createDirectories(temporary.getParent());
        Files.writeString(temporary, "avatar");
        when(values.get("avatar:upload:owner:avatars/tmp/7/expired.png")).thenReturn(null);

        service.cleanupExpiredTemporaryFiles();

        assertThat(temporary).doesNotExist();
    }
}
