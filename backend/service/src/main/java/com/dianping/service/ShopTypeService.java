package com.dianping.service;

import com.dianping.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.result.Result;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface ShopTypeService extends IService<ShopType> {

    Result<List<ShopType>> queryTypeList();
}
