package com.dianping.shopdetail;

import com.dianping.shop.setmeal.model.result.ShopSetmealResult;
import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import java.util.List;

/**
 * 店铺套餐公开查询门面。
 */
public interface ShopSetmealFacade {
    /**
     * 读取店铺上架套餐及明细，沿用公开查询参数约束。
     *
     * @param shopId 店铺标识
     * @return 包含固定菜品明细的上架套餐列表
     */
    List<ShopSetmealResult> list(Long shopId);
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    ShopPageResult<ShopSetmealResult> page(ShopListPageRequest request);
}
