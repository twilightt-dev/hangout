package com.dianping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dianping.cache.CacheClient;
import com.dianping.cache.CacheResult;
import com.dianping.cache.CacheState;
import com.dianping.constant.RedisConstants;
import com.dianping.entity.ShopType;
import com.dianping.mapper.ShopTypeMapper;
import com.dianping.result.Result;
import com.dianping.service.ShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {

    @Autowired
    CacheClient cacheClient;

    @Override
    public Result<List<ShopType>> queryTypeList() {
        CacheResult<ShopType[]> cached = cacheClient.get(
                RedisConstants.SHOP_TYPE_LIST_KEY,
                ShopType[].class
        );

        if (cached.state() == CacheState.HIT && cached.value() != null) {
            return Result.success(Arrays.asList(cached.value()));
        }

        List<ShopType> typeList = list(
                new QueryWrapper<ShopType>().orderByAsc("sort")
        );

        cacheClient.set(
                RedisConstants.SHOP_TYPE_LIST_KEY,
                typeList,
                RedisConstants.SHOP_TYPE_LIST_TTL
        );

        return Result.success(typeList);
    }
}
