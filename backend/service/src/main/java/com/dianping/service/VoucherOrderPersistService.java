package com.dianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.entity.VoucherOrder;

public interface VoucherOrderPersistService extends IService<VoucherOrder> {
    public  void createVoucherOrder(VoucherOrder voucherOrder) ;
}
