package com.dianping.controller;

import com.dianping.dto.UserDTO;
import com.dianping.result.Result;
import com.dianping.shopdetail.ShopReviewFacade;
import com.dianping.shopdetail.model.request.*;
import com.dianping.shopdetail.model.result.ShopReviewPageResult;
import com.dianping.shopdetail.model.result.ShopReviewTagOption;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/shop")
public class ShopReviewController {
    private final ShopReviewFacade facade;
    public ShopReviewController(ShopReviewFacade facade) { this.facade = facade; }
    @PostMapping("/{shopId}/reviews")
    public Result<Long> publish(@PathVariable Long shopId, @AuthenticationPrincipal UserDTO user,
                                @RequestBody PublishShopReviewRequest request) {
        return Result.success(facade.publish(shopId, user == null ? null : user.getId(), request));
    }
    @GetMapping("/{shopId}/reviews")
    public Result<ShopReviewPageResult> page(@PathVariable Long shopId,
                                              @RequestParam(defaultValue = "1") Long current) {
        return Result.success(facade.page(new ShopReviewPageRequest(shopId, current)));
    }
    @GetMapping("/review-tags")
    public Result<List<ShopReviewTagOption>> tags() { return Result.success(facade.tags()); }
}
