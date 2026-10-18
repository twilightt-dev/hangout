package com.dianping.shopdetail.service;

import com.dianping.shopdetail.exception.DuplicateRecommendDishException;
import com.dianping.shopdetail.exception.RecommendDishException;
import com.dianping.shopdetail.model.RecommendDish;
import com.dianping.shopdetail.model.request.PublishRecommendDishRequest;
import com.dianping.shopdetail.model.request.RecommendDishPageRequest;
import com.dianping.shopdetail.model.result.RecommendDishItem;
import com.dianping.shopdetail.model.result.RecommendDishPageResult;
import com.dianping.shopdetail.repository.RecommendDishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Set;

/** 推荐菜独立用例；事务内同时维护推荐关系和人数，不依赖任务链。 */
@Service
public class RecommendDishApplicationService {
    private final RecommendDishRepository repository;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public RecommendDishApplicationService(RecommendDishRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        write = transaction(manager, false);
        read = transaction(manager, true);
    }

    private static TransactionTemplate transaction(PlatformTransactionManager manager, boolean readOnly) {
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 写入由行锁保护，锁等待结束后读取最新提交；分页统计与列表使用同一快照。
        transaction.setIsolationLevel(readOnly ? TransactionDefinition.ISOLATION_REPEATABLE_READ
                : TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setReadOnly(readOnly);
        return transaction;
    }

    public RecommendDishItem publish(Long shopId, Long userId, PublishRecommendDishRequest request) {
        requireUser(userId);
        if (request == null) throw new RecommendDishException(400, "请求不得为空");
        RecommendDish submitted = request.toDish(shopId, userId);
        try {
            return write.execute(status -> {
                // 同店新增先锁店铺，再检查重名并插入，保证并发创建按顺序完成。
                if (!repository.lockShop(shopId)) throw new RecommendDishException(404, "店铺不存在");
                var existing = repository.findSubmission(userId, submitted.requestId());
                if (existing.isPresent()) return replay(existing.get(), submitted);
                if (repository.findByName(shopId, submitted.name()).isPresent()) throw nameConflict();
                Long id = repository.insert(submitted);
                repository.insertRecommendation(id, userId);
                repository.incrementRecommendationCount(id);
                return RecommendDishItem.from(repository.findById(shopId, id).orElseThrow(), true);
            });
        } catch (DuplicateRecommendDishException conflict) {
            // 原事务已回滚；跨店铺的同提交标识竞争使用新事务读取胜出记录。
            return read.execute(status -> {
                var existing = repository.findSubmission(userId, submitted.requestId());
                if (existing.isPresent()) return replay(existing.get(), submitted);
                if (repository.findByName(shopId, submitted.name()).isPresent()) throw nameConflict();
                throw conflict;
            });
        }
    }

    public RecommendDishItem recommend(Long shopId, Long dishId, Long userId) {
        requireUser(userId);
        requirePositive(shopId, "店铺");
        requirePositive(dishId, "菜品");
        return write.execute(status -> {
            if (repository.lockDish(shopId, dishId).isEmpty()) throw new RecommendDishException(404, "当前店铺不存在该菜品");
            if (!repository.hasRecommendation(dishId, userId)) {
                repository.insertRecommendation(dishId, userId);
                repository.incrementRecommendationCount(dishId);
            }
            return RecommendDishItem.from(repository.findById(shopId, dishId).orElseThrow(), true);
        });
    }

    public RecommendDishPageResult page(RecommendDishPageRequest request, Long userId) {
        if (request == null) throw new RecommendDishException(400, "请求不得为空");
        return read.execute(status -> {
            if (!repository.shopExists(request.shopId())) throw new RecommendDishException(404, "店铺不存在");
            long total = repository.countByShopId(request.shopId());
            List<RecommendDish> dishes = request.offset() >= total ? List.of()
                    : repository.page(request.shopId(), request.offset(), request.size());
            Set<Long> recommended = userId == null || dishes.isEmpty() ? Set.of()
                    : repository.recommendedIds(userId, dishes.stream().map(RecommendDish::id).toList());
            var items = dishes.stream().map(dish -> RecommendDishItem.from(dish, recommended.contains(dish.id()))).toList();
            return new RecommendDishPageResult(items, total, request.current(), request.size(),
                    total == 0 ? 0 : 1 + (total - 1) / request.size());
        });
    }

    private static RecommendDishItem replay(RecommendDish existing, RecommendDish submitted) {
        if (!existing.sameSubmission(submitted)) throw new RecommendDishException(409, "提交标识已用于其他菜品内容，请重新提交");
        return RecommendDishItem.from(existing, true);
    }
    private static RecommendDishException nameConflict() {
        return new RecommendDishException(409, "该店已收录同名菜品，请在推荐菜列表中找到它并点击“我也推荐”");
    }
    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new RecommendDishException(401, "请先登录");
    }
    private static void requirePositive(Long id, String label) {
        if (id == null || id <= 0) throw new RecommendDishException(400, label + "标识必须为正数");
    }
}
