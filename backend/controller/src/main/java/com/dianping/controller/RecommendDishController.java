package com.dianping.controller;

import com.dianping.dto.UserDTO;
import com.dianping.result.Result;
import com.dianping.shop.recommend.model.request.PublishRecommendDishRequest;
import com.dianping.shop.recommend.model.request.RecommendDishPageRequest;
import com.dianping.shop.recommend.model.result.RecommendDishItem;
import com.dianping.shop.recommend.model.result.RecommendDishPageResult;
import com.dianping.shopdetail.RecommendDishFacade;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 独立推荐菜接口；HTTP 层只处理身份、请求绑定和统一响应。
 */
@RestController
@RequestMapping("/shop/{shopId}/recommend-dishes")
public class RecommendDishController {
    /**
     * 当前业务门面。
     */
    private final RecommendDishFacade facade;
    /**
     * 创建 RecommendDishController，初始化所需依赖及实例状态。
     *
     * @param facade 当前业务门面
     */
    public RecommendDishController(RecommendDishFacade facade) { this.facade = facade; }

    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param user 服务端认证得到的用户；游客为 {@code null}
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @GetMapping
    public Result<RecommendDishPageResult> page(@PathVariable Long shopId,
            @RequestParam(defaultValue = "1") Long current, @RequestParam(defaultValue = "12") Integer size,
            @AuthenticationPrincipal UserDTO user) {
        return Result.success(facade.page(new RecommendDishPageRequest(shopId, current, size), user == null ? null : user.getId()));
    }

    /**
     * 发布推荐菜并原子维护作者首次推荐关系与人数。
     *
     * @param shopId 店铺标识
     * @param user 服务端认证得到的用户；游客为 {@code null}
     * @param request 已经定义字段约束的业务请求
     * @return 新建或幂等重放的发布结果
     */
    @PostMapping
    public Result<RecommendDishItem> publish(@PathVariable Long shopId, @AuthenticationPrincipal UserDTO user,
            @RequestBody PublishRecommendDishRequest request) {
        return Result.success(facade.publish(shopId, user == null ? null : user.getId(), request));
    }

    /**
     * 为当前用户增加一次去重推荐，重复请求不重复增加推荐人数。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @param user 服务端认证得到的用户；游客为 {@code null}
     * @return 包含最新推荐人数及当前用户推荐状态的菜品响应
     */
    @PutMapping("/{dishId}/recommendation")
    public Result<RecommendDishItem> recommend(@PathVariable Long shopId, @PathVariable Long dishId,
            @AuthenticationPrincipal UserDTO user) {
        return Result.success(facade.recommend(shopId, dishId, user == null ? null : user.getId()));
    }
}
