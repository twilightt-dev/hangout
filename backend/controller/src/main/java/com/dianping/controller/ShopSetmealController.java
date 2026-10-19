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
    public Result<List<ShopSetmealResult>> list(@PathVariable Long shopId) {
        return Result.success(facade.list(shopId));
    }
}
