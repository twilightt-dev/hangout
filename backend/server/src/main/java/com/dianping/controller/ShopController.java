package com.dianping.controller;


import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.dianping.constant.SystemConstants;
import com.dianping.result.Result;
import com.dianping.entity.Shop;
import com.dianping.exception.InvalidShopGeoQueryException;
import com.dianping.service.ShopService;
import com.dianping.service.ShopGeoValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/shop")
@Tag(name = "商铺接口")
public class ShopController {

    @Resource
    public ShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询商铺")
    public Result<Shop> queryShopById(@PathVariable("id") Long id) {

        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    @Operation(summary = "新增商铺")
    public Result<Long> saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.success(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    @Operation(summary = "更新商铺")
    public Result<Void> updateShop(@RequestBody Shop shop) {

        return shopService.update(shop) ;
    }

    /**
     * 按类型分页查询门店
     * @param typeId 店铺类型id
     * @param current 当前页码
     * @param sort 排序方式  距离、人气、评分
     * @param longitude 经度
     * @param latitude 纬度
     * @return 符合的店铺列表
     */
    @GetMapping("/of/type")
    @Operation(summary = "按类型分页查询门店")
    public Result<List<Shop>> queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "sort", defaultValue = "comments") String sort,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "latitude", required = false) Double latitude
    ) {//先检查经纬度是否合法
        if ("distance".equals(sort) && !ShopGeoValidator.isValidCoordinate(longitude, latitude)) {
            throw new InvalidShopGeoQueryException();
        }//调用服务层
        return shopService.queryByType(typeId, current, sort, longitude, latitude);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    @Operation(summary = "按名称分页查询门店")
    public Result<List<Shop>> queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StringUtils.hasText(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.success(page.getRecords());
    }
}
