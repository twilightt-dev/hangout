package com.dianping.service;

import com.dianping.result.Result;
import com.dianping.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface VoucherService extends IService<Voucher> {

    Result<List<Voucher>> queryVoucherOfShop(Long shopId);


}
