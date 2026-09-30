package com.dianping.service;

import com.dianping.result.Result;
import org.springframework.web.multipart.MultipartFile;

public interface UploadService {
    Result<String> uploadBlogImage(MultipartFile image);

    Result<String> deleteBlogImage(String imageName);
}
