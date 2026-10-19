package com.dianping.controller;

import com.dianping.dto.UserDTO;
import com.dianping.result.Result;
import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult.*;
import com.dianping.shopdetail.ShopDetailFacade;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 首屏聚合与独立列表入口；身份只来自服务端认证。
 */
@RestController
@RequestMapping("/shop/{shopId}")
public class ShopDetailController {
    /**
     * 当前业务门面。
     */
    private final ShopDetailFacade facade;
    /**
     * 创建 ShopDetailController，初始化所需依赖及实例状态。
     *
     * @param facade 当前业务门面
     */
    public ShopDetailController(ShopDetailFacade facade) { this.facade=facade; }
    /**
     * 读取商详首次展示的六区组合及当前浏览者个人状态。
     *
     * @param shopId 店铺标识
     * @param user 服务端认证得到的用户；游客为 {@code null}
     * @return 包含六区数据与分项状态的商详响应
     */
    @GetMapping("/detail")
    public Result<ShopDetailResult> detail(@PathVariable Long shopId,@AuthenticationPrincipal UserDTO user) {
        return Result.success(facade.detail(shopId,user==null?null:user.getId()));
    }
    /**
     * 读取优惠券，沿用上架、有效期和可购买优先的展示规则。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 优惠券的分页结果，包含总数与当前页记录
     */
    @GetMapping("/vouchers")
    public Result<ShopPageResult<VoucherItem>> vouchers(@PathVariable Long shopId,
            @RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="10") int size) {
        return Result.success(facade.vouchers(new ShopListPageRequest(shopId,current,size)));
    }
    /**
     * 读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param user 服务端认证得到的用户；游客为 {@code null}
     * @return 探店笔记及当前浏览者点赞状态的分页结果，包含总数与当前页记录
     */
    @GetMapping("/blogs")
    public Result<ShopPageResult<BlogItem>> blogs(@PathVariable Long shopId,
            @RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="10") int size,
            @AuthenticationPrincipal UserDTO user) {
        return Result.success(facade.blogs(new ShopListPageRequest(shopId,current,size),user==null?null:user.getId()));
    }
    /**
     * 以当前店铺为中心读取五公里内同类型店铺，排除自身并按距离、标识排序。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 带可用状态的周边分页结果
     */
    @GetMapping("/related-shops")
    public Result<SectionResult<ShopPageResult<RelatedItem>>> related(@PathVariable Long shopId,
            @RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="10") int size) {
        return Result.success(facade.related(new ShopListPageRequest(shopId,current,size)));
    }
}
