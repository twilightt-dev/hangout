package com.dianping.dto;

import lombok.Data;

/**
 * 当前登录用户的安全视图，不包含手机号、密码等敏感字段。
 */
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
