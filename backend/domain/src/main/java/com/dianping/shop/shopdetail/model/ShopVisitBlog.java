package com.dianping.shop.shopdetail.model;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * 达人探店博客值对象
 *  <p>
 *  记录用户发布的探店图文内容，包含标题、图片、正文、点赞数等。
 *  用于详情页"达人探店"区域，辅助用户做到店决策。
 *  </p>
 */
@Data
public class ShopVisitBlog {

    //主键
    /**
     * 记录标识。
     */
    private Long id;
    /**
     * 店铺标识。
     */
    private Long shopId;
    /**
     * 服务端确认的用户标识。
     */
    private Long userId;
    /**
     * 作者当前头像。
     */
    private String userIcon;
    /**
     * 作者当前昵称。
     */
    private String userName;
    /**
     * 当前用户是否点赞；读取故障时为 {@code null}。
     */
    private Boolean isLike;
    /**
     * 标题。
     */
    private String title;
    /**
     * 图片路径集合的存储文本。
     */
    private String images;
    /**
     * 正文内容。
     */
    private String content;
    /**
     * 点赞数量。
     */
    private Integer liked;
    /**
     * 评价或评论数量。
     */
    private Integer comments;
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
