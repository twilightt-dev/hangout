package com.dianping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCommentDTO {
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 255, message = "评论内容不能超过255个字符")
    private String content;
}
