package com.dianping.controller;

import com.dianping.result.Result;
import com.dianping.shop.setmeal.model.result.ShopSetmealResult;
import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shopdetail.ShopSetmealFacade;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * 套餐只读入口，游客可访问。
 */
@RestController
@RequestMapping("/shop")
public class ShopSetmealController {
    /**
     * 当前业务门面。
     */
    private final ShopSetmealFacade facade;

    /**
     * 创建 ShopSetmealController，初始化所需依赖及实例状态。
     *
     * @param facade 当前业务门面
     */
    public ShopSetmealController(ShopSetmealFacade facade) {
        this.facade = facade;
    }

    /**
     * 读取店铺上架套餐及明细，沿用公开查询参数约束。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 上架套餐分页响应
     */
    @GetMapping("/{shopId}/setmeals")
    public Result<ShopPageResult<ShopSetmealResult>> list(@PathVariable Long shopId,
            @RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") int size) {
        return Result.success(facade.page(new ShopListPageRequest(shopId, current, size)));
    }
}
