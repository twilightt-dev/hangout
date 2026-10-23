package com.dianping.shop.recommend.model.result;

import com.dianping.shop.shopdetail.model.RecommendDish;
import java.time.LocalDateTime;

/**
 * 菜品卡片读模型，不向客户端暴露提交标识等内部信息。
 *
 * @param id 记录标识
 * @param shopId 店铺标识
 * @param name 名称
 * @param coverImage 已上传的封面图片路径
 * @param recommendCount 不同用户的推荐人数
 * @param recommended 当前浏览者是否已经推荐该菜品
 * @param createTime 创建时间
 */
public record RecommendDishItem(Long id, Long shopId, String name, String coverImage,
                                long recommendCount, boolean recommended, LocalDateTime createTime) {
    /**
     * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
     *
     * @param dish 推荐菜业务数据
     * @param recommended 当前浏览者是否已经推荐该菜品
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public static RecommendDishItem from(RecommendDish dish, boolean recommended) {
        return new RecommendDishItem(dish.id(), dish.shopId(), dish.name(), dish.coverImage(),
                dish.recommendCount(), recommended, dish.createTime());
    }
}
