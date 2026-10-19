package com.dianping.controller;

import com.dianping.result.Result;
import com.dianping.shopdetail.ShopSetmealFacade;
import com.dianping.shopdetail.model.result.ShopSetmealResult;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 套餐只读入口，游客可访问。 */
@RestController
@RequestMapping("/shop")
public class ShopSetmealController {
    private final ShopSetmealFacade facade;

    public ShopSetmealController(ShopSetmealFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/{shopId}/setmeals")
    public Result<com.dianping.shopdetail.model.result.ShopPageResult<ShopSetmealResult>> list(@PathVariable Long shopId,
            @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") int size) {
        return Result.success(facade.page(new com.dianping.shopdetail.model.request.ShopDetailPageRequest(shopId, current, size)));
    }
}
