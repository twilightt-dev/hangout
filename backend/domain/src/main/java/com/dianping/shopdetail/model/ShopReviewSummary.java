package com.dianping.shopdetail.model;
import java.math.BigDecimal;
/** 全部店铺评价的统计，与近期标签样本口径分开。 */
public record ShopReviewSummary(long totalReviews, BigDecimal averageScore) {}
