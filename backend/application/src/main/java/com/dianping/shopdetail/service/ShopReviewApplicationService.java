package com.dianping.shopdetail.service;

import com.dianping.shopdetail.enums.ReviewTag;
import com.dianping.shopdetail.exception.DuplicateReviewSubmissionException;
import com.dianping.shopdetail.exception.ShopReviewException;
import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.model.request.*;
import com.dianping.shopdetail.model.result.*;
import com.dianping.shopdetail.repository.ShopReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Arrays;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 用例编排与事务边界；不读取 HTTP 或线程本地身份。 */
@Service
public class ShopReviewApplicationService {
    private final ShopReviewRepository repository;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public ShopReviewApplicationService(ShopReviewRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        write = transaction(manager, false);
        read = transaction(manager, true);
    }
    private static TransactionTemplate transaction(PlatformTransactionManager manager, boolean readOnly) {
        var template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        template.setReadOnly(readOnly);
        return template;
    }

    public Long publish(Long shopId, Long userId, PublishShopReviewRequest request) {
        if (userId == null) throw new ShopReviewException(401, "请先登录");
        if (request == null) throw new ShopReviewException(400, "请求不得为空");
        var review = request.toReview(shopId, userId);
        try {
            return write.execute(status -> {
                // 锁定前不做普通查询，避免 MySQL 提前建立旧的一致性快照。
                if (!repository.lockShop(shopId)) throw new ShopReviewException(404, "店铺不存在");
                var existing = repository.findSubmission(userId, review.requestId());
                if (existing.isPresent()) return replay(existing.get(), review);
                Long id = repository.insert(review);
                long total = repository.countByShopId(shopId);
                int score = storedScore(total, repository.sumOverallScoreByShopId(shopId));
                repository.updateStatistics(shopId, score, Math.toIntExact(total));
                return id;
            });
        } catch (DuplicateReviewSubmissionException error) {
            // execute 抛出时失败事务已经回滚；用全新视图读取唯一键竞争的胜出记录。
            return read.execute(status -> replay(repository.findSubmission(userId, review.requestId())
                    .orElseThrow(() -> error), review));
        }
    }
    private static Long replay(ShopReview existing, ShopReview submitted) {
        if (!existing.sameSubmission(submitted)) throw new ShopReviewException(409, "提交标识已被不同评价内容使用");
        return existing.id();
    }

    public ShopReviewPageResult page(ShopReviewPageRequest request) {
        return read.execute(status -> {
            if (!repository.shopExists(request.shopId())) throw new ShopReviewException(404, "店铺不存在");
            long total = repository.countByShopId(request.shopId());
            int score = storedScore(total, repository.sumOverallScoreByShopId(request.shopId()));
            var reviews = request.offset() >= total ? List.<ShopReview>of()
                    : repository.page(request.shopId(), request.offset(), ShopReviewPageRequest.SIZE);
            var items = reviews.stream().map(ShopReviewItem::from).toList();
            return new ShopReviewPageResult(items, total, request.current(), ShopReviewPageRequest.SIZE,
                    (total + ShopReviewPageRequest.SIZE - 1) / ShopReviewPageRequest.SIZE,
                    total == 0 ? null : BigDecimal.valueOf(score, 1));
        });
    }

    /** 从评分总和计算均分，保留内部十分之一分的精度，不使用历史均分反推。 */
    private static int storedScore(long total, long scoreSum) {
        if (total < 0 || total > Integer.MAX_VALUE || scoreSum < 0)
            throw new IllegalStateException("评价统计超出支持范围");
        return total == 0 ? 0 : BigDecimal.valueOf(scoreSum)
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).intValueExact();
    }
    public List<ShopReviewTagOption> tags() {
        return Arrays.stream(ReviewTag.values()).map(tag -> new ShopReviewTagOption(tag.name(), tag.displayName())).toList();
    }
}
