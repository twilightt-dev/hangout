package com.dianping.shopdetail.model.result;

import java.math.BigDecimal;
import java.util.List;

public record ShopReviewPageResult(List<ShopReviewItem> records, long total, long current, int size,
                                   long pages, BigDecimal averageScore) {
    public ShopReviewPageResult { records = List.copyOf(records); }
}
