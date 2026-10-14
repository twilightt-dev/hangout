package com.dianping.controller;


import com.dianping.result.Result;
import com.dianping.entity.ShopType;
import com.dianping.service.ShopTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
@Tag(name = "门店分类接口")
public class ShopTypeController {
    @Resource
    private ShopTypeService typeService;

    @GetMapping("list")
    @Operation(summary = "查询门店分类列表")
    public Result<List<ShopType>> queryTypeList() {
        return typeService.queryTypeList();
    }
}
