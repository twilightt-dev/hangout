package com.dianping.service.impl;

import com.dianping.VO.AvatarOptionVO;
import com.dianping.VO.AvatarVO;
import com.dianping.constant.RedisConstants;
import com.dianping.dto.AvatarUpdateDTO;
import com.dianping.dto.UserDTO;
import com.dianping.entity.User;
import com.dianping.result.Result;
import com.dianping.service.AvatarService;
import com.dianping.service.UserService;
import com.dianping.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AvatarServiceImpl implements AvatarService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final String PUBLIC_PREFIX = "/imgs/";
    private static final String TEMP_PREFIX = "avatars/tmp/";
    private static final String PERMANENT_PREFIX = "avatars/";
    private static final List<AvatarOptionVO> DEFAULT_AVATARS = List.of(
            new AvatarOptionVO("default-01", "/imgs/icons/default-01.png"),
            new AvatarOptionVO("default-02", "/imgs/icons/default-02.png"),
            new AvatarOptionVO("default-03", "/imgs/icons/default-03.png"),
            new AvatarOptionVO("default-04", "/imgs/icons/default-04.png"),
            new AvatarOptionVO("default-05", "/imgs/icons/default-05.png"),
            new AvatarOptionVO("default-06", "/imgs/icons/default-06.png"),
            new AvatarOptionVO("default-07", "/imgs/icons/default-07.png"),
            new AvatarOptionVO("default-08", "/imgs/icons/default-08.png")
    );

    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final Path baseDirectory;

    @Autowired
    public AvatarServiceImpl(UserService userService,
                             StringRedisTemplate stringRedisTemplate,
                             com.dianping.config.UploadProperties uploadProperties) {
        this(userService, stringRedisTemplate, uploadProperties.getDirectory());
    }

    public AvatarServiceImpl(UserService userService,
                             StringRedisTemplate stringRedisTemplate,
                             Path baseDirectory) {
        if (baseDirectory == null) throw new IllegalArgumentException("上传目录未配置");
        this.userService = userService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public Result<String> uploadAvatar(MultipartFile image) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) return Result.error("请先登录");
        if (image == null || image.isEmpty()) return Result.error("请选择图片文件");
        if (image.getSize() > MAX_IMAGE_BYTES) return Result.error("头像图片不能超过 5 MB");

        String suffix = suffixOf(image.getOriginalFilename());
        if (!isSupportedSuffix(suffix)) return Result.error("仅支持 JPG、PNG 格式");
        if (image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            return Result.error("文件内容类型必须为图片");
        }

        try {
            byte[] bytes = image.getBytes();
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) return Result.error("图片内容无效");
            byte[] normalized = centerCrop(source, suffix);
            String relative = TEMP_PREFIX + currentUser.getId() + "/" + UUID.randomUUID() + "." + suffix;
            Path target = safeResolve(relative);
            Files.createDirectories(target.getParent());
            Files.write(target, normalized, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                stringRedisTemplate.opsForValue().set(ownerKey(relative), currentUser.getId().toString(),
                        RedisConstants.AVATAR_UPLOAD_OWNER_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (RuntimeException redisFailure) {
                Files.deleteIfExists(target);
                return Result.error("上传服务不可用，请稍后重试");
            }
            return Result.success(PUBLIC_PREFIX + relative);
        } catch (IOException | IllegalArgumentException exception) {
            log.debug("头像上传失败", exception);
            return Result.error("头像上传失败");
        }
    }

    @Override
    public List<AvatarOptionVO> defaultAvatars() {
        return new ArrayList<>(DEFAULT_AVATARS);
    }

    @Override
    public Result<AvatarVO> updateAvatar(AvatarUpdateDTO request) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) return Result.error("请先登录");
        if (request == null || request.getSourceType() == null) return Result.error("头像来源不能为空");

        String sourceType = request.getSourceType().trim().toUpperCase(Locale.ROOT);
        boolean hasUpload = hasText(request.getUploadPath());
        boolean hasDefault = hasText(request.getDefaultId());
        if (hasUpload == hasDefault) return Result.error("只能选择一种头像来源");

        String nextIcon;
        if ("DEFAULT".equals(sourceType) && hasDefault && !hasUpload) {
            nextIcon = defaultIcon(request.getDefaultId());
            if (nextIcon == null) return Result.error("默认头像不存在");
        } else if ("UPLOAD".equals(sourceType) && hasUpload && !hasDefault) {
            nextIcon = promoteTemporaryFile(request.getUploadPath(), currentUser.getId());
            if (nextIcon == null) return Result.error("头像文件已失效，请重新上传");
        } else {
            return Result.error("头像来源参数错误");
        }

        User user = userService.getById(currentUser.getId());
        if (user == null) return Result.error("用户不存在");
        String oldIcon = user.getIcon();
        user.setIcon(nextIcon);
        if (!userService.updateById(user)) {
            if ("UPLOAD".equals(sourceType)) deleteRelative(nextIcon);
            return Result.error("头像保存失败");
        }
        deleteUserUploadIfNecessary(oldIcon, nextIcon);
        return Result.success(new AvatarVO(nextIcon, sourceType));
    }

    @Override
    @Scheduled(fixedDelayString = "${app.upload.avatar-cleanup-interval-ms:600000}", initialDelayString = "${app.upload.avatar-cleanup-initial-delay-ms:600000}")
    public void cleanupExpiredTemporaryFiles() {
        Path temporaryRoot = safeResolve(TEMP_PREFIX);
        if (!Files.isDirectory(temporaryRoot)) return;
        try (var files = Files.walk(temporaryRoot)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String relative = baseDirectory.relativize(file).toString().replace('\\', '/');
                try {
                    if (stringRedisTemplate.opsForValue().get(ownerKey(relative)) == null) {
                        Files.deleteIfExists(file);
                    }
                } catch (RuntimeException | IOException exception) {
                    log.warn("清理头像临时文件失败: {}", relative, exception);
                }
            });
        } catch (IOException exception) {
            log.warn("扫描头像临时目录失败", exception);
        }
    }

    private String promoteTemporaryFile(String publicPath, Long userId) {
        String relative = relativePath(publicPath);
        if (relative == null || !relative.startsWith(TEMP_PREFIX + userId + "/")) return null;
        String owner;
        try {
            owner = stringRedisTemplate.opsForValue().get(ownerKey(relative));
        } catch (RuntimeException exception) {
            return null;
        }
        if (!userId.toString().equals(owner)) return null;

        Path source = safeResolve(relative);
        if (!Files.isRegularFile(source)) return null;
        String filename = Path.of(relative).getFileName().toString();
        String permanent = PERMANENT_PREFIX + userId + "/" + filename;
        Path target = safeResolve(permanent);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            try {
                stringRedisTemplate.delete(ownerKey(relative));
            } catch (RuntimeException cleanupFailure) {
                // 文件已经绑定到正式路径；所有权键会自然过期，不能把已完成的保存报告为失败。
                log.warn("清理头像临时文件所有权记录失败: {}", relative, cleanupFailure);
            }
            return PUBLIC_PREFIX + permanent;
        } catch (IOException | RuntimeException exception) {
            log.debug("头像临时文件绑定失败", exception);
            return null;
        }
    }

    private String defaultIcon(String id) {
        return DEFAULT_AVATARS.stream()
                .filter(option -> option.getId().equals(id))
                .map(AvatarOptionVO::getIcon)
                .findFirst()
                .orElse(null);
    }

    private void deleteUserUploadIfNecessary(String oldIcon, String nextIcon) {
        if (oldIcon == null || oldIcon.equals(nextIcon)) return;
        if (oldIcon.startsWith(PUBLIC_PREFIX + PERMANENT_PREFIX)) deleteRelative(oldIcon);
    }

    private void deleteRelative(String publicPath) {
        String relative = relativePath(publicPath);
        if (relative == null || !relative.startsWith(PERMANENT_PREFIX)) return;
        try {
            Files.deleteIfExists(safeResolve(relative));
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("删除旧头像失败: {}", publicPath, exception);
        }
    }

    private byte[] centerCrop(BufferedImage source, String suffix) throws IOException {
        int size = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        int imageType = "png".equals(suffix) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage square = new BufferedImage(size, size, imageType);
        Graphics2D graphics = square.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, size, size, x, y, x + size, y + size, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(square, suffix, output);
        return output.toByteArray();
    }

    private Path safeResolve(String relative) {
        String clean = relative.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        Path resolved = baseDirectory.resolve(clean).normalize();
        if (!resolved.startsWith(baseDirectory)) throw new IllegalArgumentException("路径越界");
        return resolved;
    }

    private String relativePath(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(PUBLIC_PREFIX)) return null;
        String relative = publicPath.substring(PUBLIC_PREFIX.length());
        if (relative.contains("..") || relative.contains("\\")) return null;
        return relative;
    }

    private String ownerKey(String relative) {
        return RedisConstants.AVATAR_UPLOAD_OWNER_KEY + relative;
    }

    private String suffixOf(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        String suffix = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return "jpeg".equals(suffix) ? "jpg" : suffix;
    }

    private boolean isSupportedSuffix(String suffix) {
        return "jpg".equals(suffix) || "png".equals(suffix);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
