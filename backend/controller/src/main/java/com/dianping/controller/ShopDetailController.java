package com.dianping.controller;
import com.dianping.dto.UserDTO;
import com.dianping.result.Result;
import com.dianping.shopdetail.ShopDetailFacade;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.model.result.*;
import com.dianping.shopdetail.model.result.ShopDetailResult.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 首屏聚合与独立列表入口；身份只来自服务端认证。 */
@RestController
@RequestMapping("/shop/{shopId}")
public class ShopDetailController {
    private final ShopDetailFacade facade;
    public ShopDetailController(ShopDetailFacade facade) { this.facade=facade; }
    @GetMapping("/detail")
    public Result<ShopDetailResult> detail(@PathVariable Long shopId,@AuthenticationPrincipal UserDTO user) {
        return Result.success(facade.detail(shopId,user==null?null:user.getId()));
    }
    @GetMapping("/vouchers")
    public Result<ShopPageResult<VoucherItem>> vouchers(@PathVariable Long shopId,
            @RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="10") int size) {
        return Result.success(facade.vouchers(new ShopDetailPageRequest(shopId,current,size)));
    }
    @GetMapping("/blogs")
    public Result<ShopPageResult<BlogItem>> blogs(@PathVariable Long shopId,
            @RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="10") int size,
            @AuthenticationPrincipal UserDTO user) {
        return Result.success(facade.blogs(new ShopDetailPageRequest(shopId,current,size),user==null?null:user.getId()));
    }
    @GetMapping("/related-shops")
    public Result<SectionResult<ShopPageResult<RelatedItem>>> related(@PathVariable Long shopId,
            @RequestParam(defaultValue="1") long current,@RequestParam(defaultValue="10") int size) {
        return Result.success(facade.related(new ShopDetailPageRequest(shopId,current,size)));
    }
}
