package com.dianping.controller;

import com.dianping.dto.UserDTO;
import com.dianping.result.Result;
import com.dianping.shop.review.model.request.PublishShopReviewRequest;
import com.dianping.shop.review.model.request.ShopReviewPageRequest;
import com.dianping.shop.review.model.result.ShopReviewPageResult;
import com.dianping.shop.review.model.result.ShopReviewTagOption;
import com.dianping.shopdetail.ShopReviewFacade;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 业务 HTTP 入口，解析参数并显式传递服务端认证身份。
 */
@RestController
@RequestMapping("/shop")
public class ShopReviewController {
    /**
     * 当前业务门面。
     */
    private final ShopReviewFacade facade;
    /**
     * 创建 ShopReviewController，初始化所需依赖及实例状态。
     *
     * @param facade 当前业务门面
     */
    public ShopReviewController(ShopReviewFacade facade) { this.facade = facade; }
    /**
     * 发布店铺评价，幂等记录、评价写入与店铺统计在同一事务完成。
     *
     * @param shopId 店铺标识
     * @param user 服务端认证得到的用户；游客为 {@code null}
     * @param request 已经定义字段约束的业务请求
     * @return 新建或幂等重放的发布结果
     */
    @PostMapping("/{shopId}/reviews")
    public Result<Long> publish(@PathVariable Long shopId, @AuthenticationPrincipal UserDTO user,
                                @RequestBody PublishShopReviewRequest request) {
        return Result.success(facade.publish(shopId, user == null ? null : user.getId(), request));
    }
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @GetMapping("/{shopId}/reviews")
    public Result<ShopReviewPageResult> page(@PathVariable Long shopId,
                                              @RequestParam(defaultValue = "1") Long current) {
        return Result.success(facade.page(new ShopReviewPageRequest(shopId, current)));
    }
    /**
     * 读取供用户选择的固定评价标签词典。
     *
     * @return 系统预设评价标签的代码与中文名称列表
     */
    @GetMapping("/review-tags")
    public Result<List<ShopReviewTagOption>> tags() { return Result.success(facade.tags()); }
}
