package com.dianping.shop.recommend.service;

import com.dianping.shop.recommend.model.request.PublishRecommendDishRequest;
import com.dianping.shop.recommend.model.request.RecommendDishPageRequest;
import com.dianping.shop.recommend.model.result.RecommendDishItem;
import com.dianping.shop.recommend.model.result.RecommendDishPageResult;
import com.dianping.shop.shopdetail.exception.DuplicateRecommendDishException;
import com.dianping.shop.shopdetail.exception.RecommendDishException;
import com.dianping.shop.shopdetail.model.RecommendDish;
import com.dianping.shop.shopdetail.repository.RecommendDishRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 推荐菜独立用例；事务内同时维护推荐关系和人数，不依赖任务链。
 */
@Service
public class RecommendDishApplicationService {
    /**
     * 推荐菜与用户推荐关系仓储。
     */
    private final RecommendDishRepository repository;
    /**
     * 业务写入事务模板。
     */
    private final TransactionTemplate write;
    /**
     * 独立只读事务模板。
     */
    private final TransactionTemplate read;

    /**
     * 创建 RecommendDishApplicationService，初始化所需依赖及实例状态。
     *
     * @param repository 业务仓储
     * @param manager Spring 事务管理器
     */
    public RecommendDishApplicationService(RecommendDishRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        write = transaction(manager, false);
        read = transaction(manager, true);
    }

    /**
     * 创建当前用例使用的独立事务模板，配置读取或写入需要的隔离级别。
     *
     * @param manager Spring 事务管理器
     * @param readOnly 是否创建只读事务
     * @return 已配置只读标记与隔离级别的事务模板
     */
    private static TransactionTemplate transaction(PlatformTransactionManager manager, boolean readOnly) {
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 写入由行锁保护，锁等待结束后读取最新提交；分页统计与列表使用同一快照。
        transaction.setIsolationLevel(readOnly ? TransactionDefinition.ISOLATION_REPEATABLE_READ
                : TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setReadOnly(readOnly);
        return transaction;
    }

    /**
     * 发布推荐菜并原子维护作者首次推荐关系与人数。
     *
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param request 已经定义字段约束的业务请求
     * @return 新建或幂等重放的发布结果
     */
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

    /**
     * 为当前用户增加一次去重推荐，重复请求不重复增加推荐人数。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     * @return 包含最新推荐人数及当前用户推荐状态的菜品响应
     */
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

    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param request 已经定义字段约束的业务请求
     * @param userId 服务端确认的用户标识
     * @return 当前页结果；无匹配数据时为空记录集合
     */
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

    /**
     * 检查幂等记录的提交内容一致性并返回已有结果。
     *
     * @param existing 已持久化的同提交标识记录
     * @param submitted 本次提交并完成规范化的内容
     * @return 相同提交标识对应的既有菜品响应
     */
    private static RecommendDishItem replay(RecommendDish existing, RecommendDish submitted) {
        if (!existing.sameSubmission(submitted)) throw new RecommendDishException(409, "提交标识已用于其他菜品内容，请重新提交");
        return RecommendDishItem.from(existing, true);
    }
    /**
     * 创建同店同名菜品冲突的业务错误，引导用户推荐已有菜品。
     *
     * @return 提示同店同名菜品已存在的业务冲突异常
     */
    private static RecommendDishException nameConflict() {
        return new RecommendDishException(409, "该店已收录同名菜品，请在推荐菜列表中找到它并点击“我也推荐”");
    }
    /**
     * 校验写操作所需的可信登录身份。
     *
     * @param userId 服务端确认的用户标识
     */
    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new RecommendDishException(401, "请先登录");
    }
    /**
     * 校验业务标识为正数。
     *
     * @param id 记录标识
     * @param label 校验错误中使用的业务对象名称
     */
    private static void requirePositive(Long id, String label) {
        if (id == null || id <= 0) throw new RecommendDishException(400, label + "标识必须为正数");
    }
}
