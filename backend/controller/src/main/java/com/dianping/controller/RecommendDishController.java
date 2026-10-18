package com.dianping.controller;

import com.dianping.dto.UserDTO;
import com.dianping.result.Result;
import com.dianping.shopdetail.RecommendDishFacade;
import com.dianping.shopdetail.model.request.PublishRecommendDishRequest;
import com.dianping.shopdetail.model.request.RecommendDishPageRequest;
import com.dianping.shopdetail.model.result.RecommendDishItem;
import com.dianping.shopdetail.model.result.RecommendDishPageResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 独立推荐菜接口；HTTP 层只处理身份、请求绑定和统一响应。 */
@RestController
@RequestMapping("/shop/{shopId}/recommend-dishes")
public class RecommendDishController {
    private final RecommendDishFacade facade;
    public RecommendDishController(RecommendDishFacade facade) { this.facade = facade; }

    @GetMapping
    public Result<RecommendDishPageResult> page(@PathVariable Long shopId,
            @RequestParam(defaultValue = "1") Long current, @RequestParam(defaultValue = "12") Integer size,
            @AuthenticationPrincipal UserDTO user) {
        return Result.success(facade.page(new RecommendDishPageRequest(shopId, current, size), user == null ? null : user.getId()));
    }

    @PostMapping
    public Result<RecommendDishItem> publish(@PathVariable Long shopId, @AuthenticationPrincipal UserDTO user,
            @RequestBody PublishRecommendDishRequest request) {
        return Result.success(facade.publish(shopId, user == null ? null : user.getId(), request));
    }

    @PutMapping("/{dishId}/recommendation")
    public Result<RecommendDishItem> recommend(@PathVariable Long shopId, @PathVariable Long dishId,
            @AuthenticationPrincipal UserDTO user) {
        return Result.success(facade.recommend(shopId, dishId, user == null ? null : user.getId()));
    }
}
