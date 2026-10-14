package com.dianping.service;

import com.dianping.entity.SeckillVoucher;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.entity.Voucher;


public interface SeckillVoucherService extends IService<SeckillVoucher> {
    /**
     * 新增秒杀优惠券
     * @param voucher
     */
    public void addSeckillVoucher(Voucher voucher) ;
}
