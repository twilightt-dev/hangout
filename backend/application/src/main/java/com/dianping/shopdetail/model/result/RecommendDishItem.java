package com.dianping.shopdetail.model.result;

import com.dianping.shopdetail.model.RecommendDish;
import java.time.LocalDateTime;

/** 菜品卡片读模型，不向客户端暴露提交标识等内部信息。 */
public record RecommendDishItem(Long id, Long shopId, String name, String coverImage,
                                long recommendCount, boolean recommended, LocalDateTime createTime) {
    public static RecommendDishItem from(RecommendDish dish, boolean recommended) {
        return new RecommendDishItem(dish.id(), dish.shopId(), dish.name(), dish.coverImage(),
                dish.recommendCount(), recommended, dish.createTime());
    }
}
