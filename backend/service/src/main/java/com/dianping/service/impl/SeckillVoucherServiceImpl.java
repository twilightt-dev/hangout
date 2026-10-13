package com.dianping.service.impl;

import com.dianping.constant.RedisConstants;
import com.dianping.entity.SeckillVoucher;
import com.dianping.entity.Voucher;
import com.dianping.mapper.SeckillVoucherMapper;
import com.dianping.service.SeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.service.VoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements SeckillVoucherService {
    @Autowired
    VoucherService voucherService;
    @Autowired
    StringRedisTemplate stringRedisTemplate;


    /**
     * 保存秒杀优惠券
     * @param voucher
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券到优惠券表
        voucherService.save(voucher);
        // 保存优惠券到秒杀券表
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        save(seckillVoucher);
        //将秒杀库存存入redis用于快速校验
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + voucher.getId() ,
                voucher.getStock().toString()) ;


    }
}
