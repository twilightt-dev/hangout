package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.model.ShopSetmeal;
import com.dianping.shopdetail.model.ShopSetmealItem;
import com.dianping.shopdetail.persistence.mapper.ShopSetmealMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.stream.Collectors;

/** 批量读取套餐与菜品，将持久化数据转换为完整领域模型。 */
@Repository
public class ShopSetmealRepositoryImpl implements ShopSetmealRepository {
    private final ShopSetmealMapper mapper;

    public ShopSetmealRepositoryImpl(ShopSetmealMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean shopExists(Long shopId) {
        return mapper.shopExists(shopId);
    }

    @Override
    public List<ShopSetmeal> findPublishedByShopId(Long shopId) {
        var rows = mapper.findPublished(shopId);
        if (rows.isEmpty()) return List.of();
        var items = mapper.findItems(rows.stream().map(row -> row.getId()).toList()).stream()
                .collect(Collectors.groupingBy(row -> row.getSetmealId()));
        return rows.stream().map(row -> new ShopSetmeal(row.getId(), row.getShopId(), row.getName(),
                row.getCoverImage(), row.getPrice(), row.getOriginalPrice(), row.getPeople(),
                row.getDescription(), row.getRules(),
                items.getOrDefault(row.getId(), List.of()).stream()
                        .map(item -> new ShopSetmealItem(item.getName(), item.getQuantity(), item.getUnit()))
                        .toList())).toList();
    }
}
