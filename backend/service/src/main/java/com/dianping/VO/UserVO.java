package com.dianping.VO;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户主页公开信息。
 *
 * <p>不包含手机号和密码等账户敏感字段，同时聚合用户详情中的公开资料和关系统计。</p>
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String nickName;
    private String icon;
    private LocalDateTime createTime;

    private String city;
    private String introduce;
    private Integer fans;
    private Integer followee;
}
