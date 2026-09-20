package com.dianping.service;

import com.dianping.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.result.Result;
import org.springframework.web.bind.annotation.PathVariable;


public interface VoucherOrderService extends IService<VoucherOrder> {
    public Result<Long> seckillVoucher( Long voucherId);


}
