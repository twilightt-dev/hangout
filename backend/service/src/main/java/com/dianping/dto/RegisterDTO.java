package com.dianping.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "用户注册参数")
public class RegisterDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(
            regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式错误"
    )
    @Schema(
            description = "手机号",
            example = "13331814920",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(
            regexp = "^\\d{6}$",
            message = "验证码必须是6位数字"
    )
    @Schema(
            description = "注册验证码",
            example = "568383",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String verifyCode;

    @NotBlank(message = "密码不能为空")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
            message = "密码必须为8到64位，并同时包含字母和数字"
    )
    @Schema(
            description = "登录密码",
            example = "Test123456",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Schema(
            description = "确认密码",
            example = "Test123456",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String confirmPassword;
}
