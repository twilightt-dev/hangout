package com.dianping.shopdetail.impl;

import com.dianping.shop.setmeal.model.result.ShopSetmealResult;
import com.dianping.shop.setmeal.service.ShopSetmealApplicationService;
import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shopdetail.ShopSetmealFacade;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 业务门面实现，转调应用用例，不维护额外业务规则。
 */
@Component
public class ShopSetmealFacadeImpl implements ShopSetmealFacade {
    /**
     * 应用用例服务。
     */
    private final ShopSetmealApplicationService service;

    /**
     * 创建 ShopSetmealFacadeImpl，初始化所需依赖及实例状态。
     *
     * @param service 应用用例服务
     */
    public ShopSetmealFacadeImpl(ShopSetmealApplicationService service) {
        this.service = service;
    }

    /**
     * 读取店铺上架套餐及明细，沿用公开查询参数约束。
     *
     * @param shopId 店铺标识
     * @return 包含固定菜品明细的上架套餐列表
     */
    @Override
    public List<ShopSetmealResult> list(Long shopId) {
        return service.list(shopId);
    }

    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    public ShopPageResult<ShopSetmealResult> page(
            ShopListPageRequest request) {
        return service.page(request);
    }
}
