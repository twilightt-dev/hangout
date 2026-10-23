package com.dianping.shopdetail.repository;

import com.dianping.shop.shopdetail.model.ShopSetmeal;
import com.dianping.shop.shopdetail.model.ShopSetmealItem;
import com.dianping.shop.shopdetail.model.ShopPage;
import com.dianping.shop.shopdetail.repository.ShopSetmealRepository;
import org.springframework.transaction.annotation.*;
import com.dianping.shopdetail.persistence.mapper.ShopSetmealMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 批量读取套餐与菜品，将持久化数据转换为完整领域模型。
 */
@Repository
public class ShopSetmealRepositoryImpl implements ShopSetmealRepository {
    /**
     * 数据映射器。
     */
    private final ShopSetmealMapper mapper;

    /**
     * 创建 ShopSetmealRepositoryImpl，初始化所需依赖及实例状态。
     *
     * @param mapper 当前业务的 MyBatis 数据访问接口
     */
    public ShopSetmealRepositoryImpl(ShopSetmealMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 检查店铺记录是否存在。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在
     */
    @Override
    public boolean shopExists(Long shopId) {
        return mapper.shopExists(shopId);
    }

    /**
     * 查询当前店铺的上架套餐并批量补齐固定菜品明细。
     *
     * @param shopId 店铺标识
     * @return 有序上架套餐列表，每项包含固定菜品明细
     */
    @Override
    public List<ShopSetmeal> findPublishedByShopId(Long shopId) {
        return map(mapper.findPublished(shopId));
    }

    /**
     * 统计当前店铺上架套餐总数。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    public long countPublished(Long shopId) { return mapper.countPublished(shopId); }

    /**
     * 按展示顺序分页读取上架套餐。
     *
     * @param shopId 店铺标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    public List<ShopSetmeal> pagePublished(Long shopId, long offset, int size) {
        return map(mapper.pagePublished(shopId, offset, size));
    }

    /**
     * 批量补齐套餐菜品明细并映射为领域模型。
     *
     * @param rows 有序数据库记录集合
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    private List<ShopSetmeal> map(List<com.dianping.shopdetail.persistence.dataobject.ShopSetmeal> rows) {
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

    /**
     * 在同一读取快照内取得分页明细和总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public ShopPage<ShopSetmeal> pageWithTotal(Long shopId, long current, int size) {
        return new ShopPage<>(pagePublished(shopId, Math.multiplyExact(current - 1, (long) size), size),
                countPublished(shopId), current, size);
    }
}
