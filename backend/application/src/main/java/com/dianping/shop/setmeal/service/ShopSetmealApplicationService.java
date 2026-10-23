package com.dianping.shop.setmeal.service;

import com.dianping.shop.setmeal.model.result.ShopSetmealResult;
import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shop.shopdetail.exception.ShopSetmealException;
import com.dianping.shop.shopdetail.model.ShopPage;
import com.dianping.shop.shopdetail.repository.ShopSetmealRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 在同一只读快照内编排套餐主信息与明细查询。
 */
@Service
public class ShopSetmealApplicationService {
    /**
     * 店铺套餐仓储。
     */
    private final ShopSetmealRepository repository;
    /**
     * 独立只读事务模板。
     */
    private final TransactionTemplate read;

    /**
     * 创建 ShopSetmealApplicationService，初始化所需依赖及实例状态。
     *
     * @param repository 业务仓储
     * @param manager Spring 事务管理器
     */
    public ShopSetmealApplicationService(ShopSetmealRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * 读取店铺上架套餐及明细，沿用公开查询参数约束。
     *
     * @param shopId 店铺标识
     * @return 包含固定菜品明细的上架套餐列表
     */
    public List<ShopSetmealResult> list(Long shopId) {
        if (shopId == null || shopId <= 0) throw new ShopSetmealException(400, "店铺 ID 必须为正整数");
        return read.execute(status -> {
            if (!repository.shopExists(shopId)) throw new ShopSetmealException(404, "店铺不存在");
            return repository.findPublishedByShopId(shopId).stream().map(ShopSetmealResult::from).toList();
        });
    }

    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    public ShopPageResult<ShopSetmealResult> page(
            ShopListPageRequest request) {
        return read.execute(status -> {
            if (!repository.shopExists(request.shopId())) throw new ShopSetmealException(404, "店铺不存在");
            var page = new ShopPage<>(
                    repository.pagePublished(request.shopId(), request.offset(), request.size()),
                    repository.countPublished(request.shopId()), request.current(), request.size());
            return ShopPageResult.from(page, ShopSetmealResult::from);
        });
    }
}
