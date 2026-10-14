package com.dianping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "手机号不能为空")
    private String phone;

    private String loginType ;

    private String verifyCode ;

    private String password;
}
