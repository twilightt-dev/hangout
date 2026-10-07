package com.dianping.controller;

import com.dianping.VO.AvatarOptionVO;
import com.dianping.VO.AvatarVO;
import com.dianping.dto.AvatarUpdateDTO;
import com.dianping.result.Result;
import com.dianping.service.AvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@Tag(name = "头像接口")
public class AvatarController {
    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PostMapping("/upload/avatar")
    @Operation(summary = "上传临时头像")
    public Result<String> uploadAvatar(@RequestParam("image") MultipartFile image) {
        return avatarService.uploadAvatar(image);
    }

    @GetMapping("/user/avatar/defaults")
    @Operation(summary = "查询默认头像")
    public Result<List<AvatarOptionVO>> defaultAvatars() {
        return Result.success(avatarService.defaultAvatars());
    }

    @PutMapping("/user/avatar")
    @Operation(summary = "保存当前头像")
    public Result<AvatarVO> updateAvatar(@RequestBody AvatarUpdateDTO request) {
        return avatarService.updateAvatar(request);
    }
}
