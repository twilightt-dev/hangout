package com.dianping.shop.recommend.model.request;

import com.dianping.shop.shopdetail.model.RecommendDish;
import java.time.LocalDateTime;

/**
 * 作者与店铺从可信调用参数取得，不接受请求体覆盖。
 *
 * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
 * @param name 名称
 * @param coverImage 已上传的封面图片路径
 */
public record PublishRecommendDishRequest(String requestId, String name, String coverImage) {
    /**
     * 将发布请求转换为经过领域校验的推荐菜，创建时间取当前服务端时间。
     *
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @return 尚未持久化的合法推荐菜领域对象
     */
    public RecommendDish toDish(Long shopId, Long userId) {
        return new RecommendDish(null, shopId, userId, requestId, name, coverImage, 0, LocalDateTime.now());
    }
}
