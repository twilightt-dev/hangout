package com.dianping.shopdetail.impl;

import com.dianping.shopdetail.ShopSetmealFacade;
import com.dianping.shopdetail.model.result.ShopSetmealResult;
import com.dianping.shopdetail.service.ShopSetmealApplicationService;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ShopSetmealFacadeImpl implements ShopSetmealFacade {
    private final ShopSetmealApplicationService service;

    public ShopSetmealFacadeImpl(ShopSetmealApplicationService service) {
        this.service = service;
    }

    @Override
    public List<ShopSetmealResult> list(Long shopId) {
        return service.list(shopId);
    }

    public com.dianping.shopdetail.model.result.ShopPageResult<ShopSetmealResult> page(
            com.dianping.shopdetail.model.request.ShopDetailPageRequest request) {
        return service.page(request);
    }
}
