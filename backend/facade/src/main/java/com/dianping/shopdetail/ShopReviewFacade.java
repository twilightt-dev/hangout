package com.dianping.shopdetail;

import com.dianping.shopdetail.model.request.PublishShopReviewRequest;
import com.dianping.shopdetail.model.request.ShopReviewPageRequest;
import com.dianping.shopdetail.model.result.ShopReviewPageResult;
import com.dianping.shopdetail.model.result.ShopReviewTagOption;
import java.util.List;

public interface ShopReviewFacade {
    Long publish(Long shopId, Long userId, PublishShopReviewRequest request);
    ShopReviewPageResult page(ShopReviewPageRequest request);
    List<ShopReviewTagOption> tags();
}
