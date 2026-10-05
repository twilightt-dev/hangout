package com.dianping.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 当前用户可编辑的资料字段。
 */
@Data
public class UpdateUserInfoDTO {

    @Size(max = 64, message = "城市名称不能超过64个字符")
    private String city;

    @Size(max = 128, message = "个人介绍不能超过128个字符")
    private String introduce;

    private Boolean gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;
}
