package com.dianping.shopdetail;

import com.dianping.shop.review.model.request.PublishShopReviewRequest;
import com.dianping.shop.review.model.request.ShopReviewPageRequest;
import com.dianping.shop.review.model.result.ShopReviewPageResult;
import com.dianping.shop.review.model.result.ShopReviewTagOption;
import java.util.List;

/**
 * 面向控制器的业务门面，隐藏应用层编排并返回业务响应。
 */
public interface ShopReviewFacade {
    /**
     * 发布店铺评价，幂等记录、评价写入与店铺统计在同一事务完成。
     *
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param request 已经定义字段约束的业务请求
     * @return 新建或幂等重放的发布结果
     */
    Long publish(Long shopId, Long userId, PublishShopReviewRequest request);
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    ShopReviewPageResult page(ShopReviewPageRequest request);
    /**
     * 读取供用户选择的固定评价标签词典。
     *
     * @return 系统预设评价标签的代码与中文名称列表
     */
    List<ShopReviewTagOption> tags();
}
