package com.dianping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.constant.RedisConstants;
import com.dianping.result.Result;
import com.dianping.entity.Voucher;
import com.dianping.mapper.VoucherMapper;
import com.dianping.entity.SeckillVoucher;
import com.dianping.service.SeckillVoucherService;
import com.dianping.service.VoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;


@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<List<Voucher>> queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.success(vouchers);
    }



}
