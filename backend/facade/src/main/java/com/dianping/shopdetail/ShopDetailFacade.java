package com.dianping.shopdetail;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.model.result.*;
import com.dianping.shopdetail.model.result.ShopDetailResult.*;
/** 商详读取门面；不包装 HTTP 外壳。 */
public interface ShopDetailFacade {
    ShopDetailResult detail(Long shopId, Long viewerId);
    ShopPageResult<VoucherItem> vouchers(ShopDetailPageRequest request);
    ShopPageResult<BlogItem> blogs(ShopDetailPageRequest request, Long viewerId);
    SectionResult<ShopPageResult<RelatedItem>> related(ShopDetailPageRequest request);
}
