package com.dianping.controller;


import com.dianping.result.Result;
import com.dianping.entity.Voucher;
import com.dianping.service.SeckillVoucherService;
import com.dianping.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/voucher")
@Tag(name = "优惠券接口")
public class VoucherController {

    @Autowired
    private VoucherService voucherService;
    @Autowired
    private SeckillVoucherService seckillVoucherService;

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    @Operation(summary = "新增普通优惠券")
    public Result<Long> addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.success(voucher.getId());
    }

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    @Operation(summary = "新增秒杀优惠券")
    public Result<Long> addSeckillVoucher(@RequestBody Voucher voucher) {
        seckillVoucherService.addSeckillVoucher(voucher);
        return Result.success(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    @Operation(summary = "查询门店优惠券列表")
    public Result<List<Voucher>> queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }



}
