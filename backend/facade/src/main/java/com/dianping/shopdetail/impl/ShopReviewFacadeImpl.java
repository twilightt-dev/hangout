package com.dianping.shopdetail.impl;

import com.dianping.shopdetail.ShopReviewFacade;
import com.dianping.shopdetail.model.request.*;
import com.dianping.shopdetail.model.result.*;
import com.dianping.shopdetail.service.ShopReviewApplicationService;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ShopReviewFacadeImpl implements ShopReviewFacade {
    private final ShopReviewApplicationService service;
    public ShopReviewFacadeImpl(ShopReviewApplicationService service) { this.service = service; }
    public Long publish(Long shopId, Long userId, PublishShopReviewRequest request) { return service.publish(shopId,userId,request); }
    public ShopReviewPageResult page(ShopReviewPageRequest request) { return service.page(request); }
    public List<ShopReviewTagOption> tags() { return service.tags(); }
}
