package com.dianping.shopdetail.model.result;

import java.util.List;

public record RecommendDishPageResult(List<RecommendDishItem> records, long total, long current, int size, long pages) {
    public RecommendDishPageResult { records = List.copyOf(records); }
}
