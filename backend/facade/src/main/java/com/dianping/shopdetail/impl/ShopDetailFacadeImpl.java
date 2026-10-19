package com.dianping.shopdetail.impl;
import com.dianping.shopdetail.ShopDetailFacade;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.model.result.*;
import com.dianping.shopdetail.model.result.ShopDetailResult.*;
import com.dianping.shopdetail.service.ShopDetailApplicationService;
import org.springframework.stereotype.Component;
@Component
public class ShopDetailFacadeImpl implements ShopDetailFacade {
    private final ShopDetailApplicationService service;
    public ShopDetailFacadeImpl(ShopDetailApplicationService service) { this.service=service; }
    public ShopDetailResult detail(Long shopId, Long viewerId) { return service.getShopDetail(shopId,viewerId); }
    public ShopPageResult<VoucherItem> vouchers(ShopDetailPageRequest request) { return service.vouchers(request); }
    public ShopPageResult<BlogItem> blogs(ShopDetailPageRequest request,Long viewerId) { return service.blogs(request,viewerId); }
    public SectionResult<ShopPageResult<RelatedItem>> related(ShopDetailPageRequest request) { return service.related(request); }
}
