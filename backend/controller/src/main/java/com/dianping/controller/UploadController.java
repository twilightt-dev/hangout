package com.dianping.controller;

import com.dianping.config.UploadProperties;
import com.dianping.constant.RedisConstants;
import com.dianping.dto.UserDTO;
import com.dianping.result.Result;
import com.dianping.service.UploadService;
import com.dianping.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@RestController
@RequestMapping("/upload")
@Tag(name = "文件上传接口")
public class UploadController {
    @Autowired
    private UploadService uploadService;

    @PostMapping("/blog/uploadImage")
    @Operation(summary = "上传博客图片")
    public Result<String> uploadBlogImage(@RequestParam("image") MultipartFile image)  {
        return uploadService.uploadBlogImage(image) ;
    }

    @DeleteMapping("/blog/deleteImage")
    @Operation(summary = "删除博客图片")
    public Result<String> deleteBlogImage(@RequestParam("name") String imageName) {
        return uploadService.deleteBlogImage(imageName) ;
    }
}
