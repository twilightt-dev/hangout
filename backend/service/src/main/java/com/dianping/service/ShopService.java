package com.dianping.service;

import com.dianping.entity.Shop;
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
public interface ShopService extends IService<Shop> {

    Result<Shop> queryById(Long id);

    Result<Void> update(Shop shop);

    Result<List<Shop>> queryByType(Integer typeId, Integer current, String sort,
                                    Double longitude, Double latitude);
}
