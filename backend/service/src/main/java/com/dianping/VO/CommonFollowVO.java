package com.dianping.VO;

import lombok.Data;

import java.io.Serializable;

/**
 * 共同关注列表中的用户公开信息。
 */
@Data
public class CommonFollowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String nickName;
    private String icon;
}
