package com.dianping.shopdetail.service;

import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.model.result.SectionResult;
import com.dianping.shopdetail.exception.ShopDetailException;
import com.dianping.shopdetail.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** 首屏节点和独立分页复用的读取用例；每个查询在工作线程内创建自己的只读事务。 */
@Service
public class ShopDetailQueryService {
    private final ShopDetailRepository details;
    private final ShopVoucherRepository vouchers;
    private final ShopSetmealRepository meals;
    private final ShopReviewRepository reviews;
    private final RecommendDishRepository dishes;
    private final ShopDetailDomainService domain;
    private final TransactionTemplate read;
    public ShopDetailQueryService(ShopDetailRepository details, ShopVoucherRepository vouchers,
            ShopSetmealRepository meals, ShopReviewRepository reviews, RecommendDishRepository dishes,
            PlatformTransactionManager manager, ShopDetailDomainService domain) {
        this.details = details; this.vouchers = vouchers; this.meals = meals;
        this.reviews = reviews; this.dishes = dishes;
        this.domain = domain;
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    private <T> T read(Supplier<T> query) { return read.execute(status -> query.get()); }
    public ShopBaseInfo base(Long id) {
        new ShopDetailPageRequest(id, 1, 1);
        return read(() -> details.findShop(id).orElseThrow(() -> new ShopDetailException(404, "店铺不存在")));
    }
    public ShopPage<ShopVoucher> vouchers(ShopDetailPageRequest request) {
        return read(() -> vouchers.page(request.shopId(), request.current(), request.size(), LocalDateTime.now()));
    }
    public ShopPage<ShopSetmeal> setmeals(ShopDetailPageRequest request) {
        return read(() -> new ShopPage<>(meals.pagePublished(request.shopId(), request.offset(), request.size()),
                meals.countPublished(request.shopId()), request.current(), request.size()));
    }
    public ShopPage<RecommendDish> recommendations(ShopDetailPageRequest request) {
        return read(() -> new ShopPage<>(dishes.page(request.shopId(), request.offset(), request.size()),
                dishes.countByShopId(request.shopId()), request.current(), request.size()));
    }
    public Set<Long> recommended(Long userId, ShopPage<RecommendDish> page) {
        if (userId == null || page.records().isEmpty()) return Set.of();
        return read(() -> Set.copyOf(dishes.recommendedIds(userId, page.records().stream().map(RecommendDish::id).toList())));
    }
    public ShopPage<ShopReview> reviews(ShopDetailPageRequest request) {
        return read(() -> new ShopPage<>(reviews.page(request.shopId(), request.offset(), request.size()),
                reviews.countByShopId(request.shopId()), request.current(), request.size()));
    }
    public ShopReviewSummary statistics(Long shopId) {
        return read(() -> {
            long total = reviews.countByShopId(shopId), sum = reviews.sumOverallScoreByShopId(shopId);
            BigDecimal average = total == 0 ? null : BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(total).multiply(BigDecimal.TEN), 1, RoundingMode.HALF_UP);
            return new ShopReviewSummary(total, average);
        });
    }
    public ShopPage<ShopVisitBlog> blogs(ShopDetailPageRequest request, Long userId) {
        return read(() -> details.blogs(request.shopId(), request.current(), request.size(), userId));
    }
    public List<TagEvidence> reviewEvidence(Long shopId, LocalDateTime since) {
        return read(() -> details.reviewEvidence(shopId, since, 200));
    }
    public List<TagEvidence> blogEvidence(Long shopId, LocalDateTime since) {
        return read(() -> details.blogEvidence(shopId, since, 50));
    }
    public ShopPage<ShopRelatedShop> related(ShopBaseInfo center, ShopDetailPageRequest request) {
        return read(() -> domain.buildRelatedShops(details, center, request.current(), request.size()));
    }

    public boolean hasCoordinates(ShopBaseInfo center) { return domain.hasCoordinates(center); }
    /** 分项故障只影响自身，日志保留完整异常，对外消息固定。 */
    public static <T> SectionResult<T> attempt(Supplier<T> query, Predicate<T> empty) {
        try { T value = query.get(); return SectionResult.success(value, empty.test(value)); }
        catch (RuntimeException error) {
            org.apache.commons.logging.LogFactory.getLog(ShopDetailQueryService.class).warn("商详局部查询失败", error);
            return SectionResult.unavailable("FAILED", "暂时不可用，请稍后重试");
        }
    }
}
