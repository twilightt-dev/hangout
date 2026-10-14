package com.dianping.service;

import com.dianping.VO.AvatarOptionVO;
import com.dianping.VO.AvatarVO;
import com.dianping.dto.AvatarUpdateDTO;
import com.dianping.result.Result;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AvatarService {
    Result<String> uploadAvatar(MultipartFile image);

    List<AvatarOptionVO> defaultAvatars();

    Result<AvatarVO> updateAvatar(AvatarUpdateDTO request);

    void cleanupExpiredTemporaryFiles();
}
