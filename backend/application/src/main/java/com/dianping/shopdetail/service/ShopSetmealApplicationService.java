package com.dianping.shopdetail.service;

import com.dianping.shopdetail.model.result.ShopSetmealResult;
import com.dianping.shopdetail.exception.ShopSetmealException;
import com.dianping.shopdetail.repository.ShopSetmealRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

/** 在同一只读快照内编排套餐主信息与明细查询。 */
@Service
public class ShopSetmealApplicationService {
    private final ShopSetmealRepository repository;
    private final TransactionTemplate read;

    public ShopSetmealApplicationService(ShopSetmealRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public List<ShopSetmealResult> list(Long shopId) {
        if (shopId == null || shopId <= 0) throw new ShopSetmealException(400, "店铺 ID 必须为正整数");
        return read.execute(status -> {
            if (!repository.shopExists(shopId)) throw new ShopSetmealException(404, "店铺不存在");
            return repository.findPublishedByShopId(shopId).stream().map(ShopSetmealResult::from).toList();
        });
    }

    public com.dianping.shopdetail.model.result.ShopPageResult<ShopSetmealResult> page(
            com.dianping.shopdetail.model.request.ShopDetailPageRequest request) {
        return read.execute(status -> {
            if (!repository.shopExists(request.shopId())) throw new ShopSetmealException(404, "店铺不存在");
            var page = new com.dianping.shopdetail.model.ShopPage<>(
                    repository.pagePublished(request.shopId(), request.offset(), request.size()),
                    repository.countPublished(request.shopId()), request.current(), request.size());
            return com.dianping.shopdetail.model.result.ShopPageResult.from(page, ShopSetmealResult::from);
        });
    }
}
