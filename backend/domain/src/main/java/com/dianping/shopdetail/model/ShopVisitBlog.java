package com.dianping.shopdetail.model;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 达人探店博客值对象
 * <p>
 * 记录用户发布的探店图文内容，包含标题、图片、正文、点赞数等。
 * 用于详情页"达人探店"区域，辅助用户做到店决策。
 * </p>
 */
@Data
public class ShopVisitBlog {

    //主键
    private Long id;
    private Long shopId;
    private Long userId;
    private String userIcon;
    private String userName;
    private Boolean isLike;
    private String title;
    private String images;
    private String content;
    private Integer liked;
    private Integer comments;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
