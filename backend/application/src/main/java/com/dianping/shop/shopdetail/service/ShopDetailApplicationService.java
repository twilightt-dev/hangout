package com.dianping.shop.shopdetail.service;

import com.dianping.shared.taskchain.enums.*;
import com.dianping.shop.cache.ShopCacheAccessException;
import com.dianping.shop.cache.ShopCacheProtection;
import com.dianping.shared.taskchain.model.PhaseConfig;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.recommend.model.result.RecommendDishItem;
import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult.BlogItem;
import com.dianping.shop.shopdetail.task.ShopDetailTaskContext;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheCoordinator;
import com.dianping.shop.shopdetail.exception.ShopDetailException;
import com.dianping.shop.shopdetail.model.ShopDetailAggregate;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * 商详首次展示用例：选择执行路径、编排任务并检查执行结果。
 */
@Service
public class ShopDetailApplicationService {
    /**
     * 共享任务链引擎；允许未装配。
     */
    private final TaskChainEngine engine;
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 商详任务链阶段的执行配置。
     */
    private final List<PhaseConfig> phases;
    /**
     * 公共商详缓存协调器；无缓存配置时沿用实时查询。
     */
    private final ShopDetailCacheCoordinator<ShopDetailResult> cache;
    /**
     * 独立优惠券查询及用户状态读取用例。
     */
    private final ShopDetailQueryService queries;
    /**
     * 是否启用公共快照缓存。
     */
    private final boolean cacheEnabled;
    /** 关闭公共快照缓存时仍需保护商详入口，不让开关绕过防穿透。 */
    @Autowired(required = false)
    private ShopCacheProtection protection;

    /** 缓存关闭时仍沿用同一份前台等待预算。 */
    @Value("${shop-cache.lock-wait-ms:2000}")
    private long protectionWaitMillis = 2000;

    /**
     * 创建 ShopDetailApplicationService，初始化所需依赖及实例状态。
     * @param engine 共享任务链引擎；允许未装配
     * @param domain 商详领域服务
     * @param queryTimeout 并行查询阶段总预算，单位为毫秒
     * @param postTimeout 后处理阶段共享预算，单位为毫秒
     */
    public ShopDetailApplicationService(@Nullable TaskChainEngine engine, ShopDetailDomainService domain,
            @Value("${shop-detail.query-timeout-ms:2000}") long queryTimeout,
            @Value("${shop-detail.post-timeout-ms:1000}") long postTimeout) {
        this(engine, domain, queryTimeout, postTimeout, null, null, false);
    }

    /**
     * 创建启用可选公共缓存的商详应用用例。
     *
     * @param engine 共享任务链引擎；允许未装配
     * @param domain 商详领域服务
     * @param queryTimeout 并行查询预算，单位为毫秒
     * @param postTimeout 后处理预算，单位为毫秒
     * @param cache 公共快照协调器
     * @param queries 实时优惠券与个人状态查询用例
     * @param cacheEnabled 公共快照缓存开关
     */
    @Autowired
    public ShopDetailApplicationService(@Nullable TaskChainEngine engine, ShopDetailDomainService domain,
            @Value("${shop-detail.query-timeout-ms:2000}") long queryTimeout,
            @Value("${shop-detail.post-timeout-ms:1000}") long postTimeout,
            @Nullable ShopDetailCacheCoordinator<ShopDetailResult> cache,
            @Nullable ShopDetailQueryService queries,
            @Value("${shop-detail.cache-enabled:true}") boolean cacheEnabled) {
        this.engine = engine;
        this.domain = domain;
        this.cache = cache;
        this.queries = queries;
        this.cacheEnabled = cacheEnabled;
        phases = List.of(
                new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.PARALLEL, Duration.ofMillis(queryTimeout)),
                new PhaseConfig(ProcessPhase.POST_PROCESSING, ExecutionMode.SERIAL, Duration.ofMillis(postTimeout)));
    }

    /**
     * 选择走缓存还是走实时查询
     * @param shopId 店铺标识
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 包含六个展示区域及各分项状态的商详响应
     * @throws ShopDetailException 输入非法、店铺不存在，或必需查询失败、超时
     */
    public ShopDetailResult getShopDetail(Long shopId, Long viewerId) {
        //校验店铺ID
        if (shopId == null || shopId <= 0) throw new ShopDetailException(400, "店铺 ID 必须为正整数");
        //已经装配缓存协调器+开启公共缓存+装配实时查询服务才能走公共缓存
        if (cache != null && cacheEnabled && queries != null) {
            var snapshot = cache.getOrLoad(shopId, () -> publicSnapshot(loadUncached(shopId, null, false)));
            //补齐个人状态
            return attachRequestData(snapshot, shopId, viewerId);
        }
        //实时查询
        return loadWithProtection(shopId, viewerId);
    }

    /** 关闭公共快照时仍检查存在性并按同店互斥回源；锁不可用则仅实时查询。 */
    private ShopDetailResult loadWithProtection(Long shopId, Long viewerId) {
        if (protection != null) {
            //用布隆过滤器+空值标记拦截不存在的店铺
            if (protection.isNotFound(shopId) || !protection.mayExist(shopId))
                throw new ShopDetailException(404, "店铺不存在");
            ShopCacheProtection.Lease lease;
            //获取重建锁
            try {
                lease = protection.tryLock(shopId, Duration.ofMillis(protectionWaitMillis));
            }//获取锁失败就降级走实时查询
            catch (ShopCacheAccessException failure) {
                return loadUncached(shopId, viewerId);
            }
            //503表示服务异常
            if (lease == null) throw new ShopDetailException(503, "商详暂时不可用，请稍后重试");
            //try-with-resources自动释放锁
            try (lease) {
                //double-check，确认不存在就返回404
                if (protection.isNotFound(shopId)) throw new ShopDetailException(404, "店铺不存在");
                try {
                    //实时查询
                    return loadUncached(shopId, viewerId);
                }
                catch (ShopDetailException failure) {
                    if (failure.status() == 404) {
                        try {
                            protection.markNotFound(shopId);
                        }
                        catch (ShopCacheAccessException ignored) { /* 不改变已确认的不存在结果。 */ }
                    }
                    throw failure;
                }
            }
        }
        //没装配保护服务就直接实时查询
        return loadUncached(shopId, viewerId);
    }

    private ShopDetailResult loadUncached(Long shopId, Long viewerId) {
        return loadUncached(shopId, viewerId, true);
    }


    /**
     * 实时查询商详数据
     * @param shopId 店铺标识
     * @param viewerId 用户id
     * @param includeVouchers  是否包含优惠券
     * @return
     */
    private ShopDetailResult loadUncached(Long shopId, Long viewerId, boolean includeVouchers) {
        ShopDetailAggregate detail;
        //不走任务链，直接调用领域服务的串行查询方法
        if (engine == null) {
            try {
                detail = domain.getShopDetail(shopId, viewerId, includeVouchers);
            } catch (RuntimeException error) {
                throw failure(shopId, error);
            }
            //有任务链就执行任务链
        } else {
            var context = engine.executePhaseChain(ShopDetailTaskContext.GROUP,
                    new ShopDetailTaskContext(shopId, viewerId, includeVouchers), phases);
            ensureSuccess(context);
            return ShopDetailResult.from(context.getOutput(), context.sectionResults());
        }
        return ShopDetailResult.from(detail);
    }


    /**
     * 强制重建一间店铺的公共商详快照，供定时任务调用。
     * @param shopId 店铺标识
     * @return 是否取得重建锁并成功写入
     */
    public boolean warmShop(Long shopId) {
        if (cache == null || !cacheEnabled) return false;
        if (shopId == null || shopId <= 0) throw new IllegalArgumentException("店铺 ID 必须为正整数");
        return cache.refresh(shopId, () -> publicSnapshot(loadUncached(shopId, null, false)));
    }

    /**
     * 取得当前店铺 ID 列表，供 job 层按稳定顺序全量预热。
     * @return 当前全部店铺标识
     */
    public List<Long> allShopIds() { return domain.allShopIds(); }


    /**
     * 将查询到的商详结果封装为公共快照
     * @param response 商详结果
     * @return 一个公共快照，个人状态清除，优惠券实时查询
     */
    private ShopDetailResult publicSnapshot(ShopDetailResult response) {
        //把推荐菜设为false，清除用户个人状态
        var recommendations = response.recommendations();
        if (recommendations != null && recommendations.data() != null) {
            var page = recommendations.data();
            var records = page.records().stream().map(item -> new RecommendDishItem(item.id(), item.shopId(), item.name(),
                    item.coverImage(), item.recommendCount(), false, item.createTime())).toList();
            recommendations = new SectionResult<>(recommendations.status(),
                    new ShopPageResult<>(records, page.total(), page.current(), page.size(), page.pages()), recommendations.message());
        }

        //依旧保留公共内容，将isLike置为null
        var blogs = response.blogs();
        if (blogs != null && blogs.data() != null) {
            var page = blogs.data();
            var records = page.records().stream().map(item -> new BlogItem(item.id(), item.shopId(), item.userId(),
                    item.name(), item.icon(), null, item.title(), item.images(), item.content(), item.liked(),
                    item.comments(), item.createTime())).toList();
            blogs = new SectionResult<>(blogs.status(),
                    new ShopPageResult<>(records, page.total(), page.current(), page.size(), page.pages()), blogs.message());
        }

        return new ShopDetailResult(response.shop(), SectionResult.unavailable("UNAVAILABLE", "优惠券实时查询中"),
                response.setmeals(), recommendations, response.reviews(), response.statistics(), blogs,
                response.decisionTags(), response.reviewTags(), response.relatedShops(), false);
    }


    /**
     * 在公共快照上补齐当前用户的推荐、点赞状态，再实时查询优惠券，得到最终结果
     * @param snapshot 公共快照
     * @param shopId 店铺标识
     * @param viewerId 浏览者id，游客为{@code null}
     * @return 完整的结果
     */
    private ShopDetailResult attachRequestData(ShopDetailResult snapshot, Long shopId, Long viewerId) {
        //游客不需要推荐关系
        var recommendationStateAvailable = viewerId == null;
        var recommendations = snapshot.recommendations();
        if (viewerId != null && recommendations != null && recommendations.data() != null) {
            try {
                var ids = recommendations.data().records().stream().map(RecommendDishItem::id).toList();
                Set<Long> recommended = queries.recommendedIds(viewerId, ids);
                var page = recommendations.data();
                var records = page.records().stream().map(item -> new RecommendDishItem(item.id(), item.shopId(), item.name(),
                        item.coverImage(), item.recommendCount(), recommended.contains(item.id()), item.createTime())).toList();
                recommendations = new SectionResult<>(recommendations.status(),
                        new ShopPageResult<>(records, page.total(), page.current(), page.size(), page.pages()), recommendations.message());
                recommendationStateAvailable = true;
            } catch (RuntimeException failure) {
                org.apache.commons.logging.LogFactory.getLog(getClass()).warn("商详推荐状态补齐失败，shopId=" + shopId, failure);
                recommendationStateAvailable = false;
            }
        }

        var blogs = snapshot.blogs();
        if (viewerId != null && blogs != null && blogs.data() != null) {
            try {
                var page = blogs.data();
                var ids = page.records().stream().map(BlogItem::id).toList();
                Set<Long> liked = queries.likedBlogIds(viewerId, ids);
                var records = page.records().stream().map(item -> new BlogItem(item.id(), item.shopId(), item.userId(),
                        item.name(), item.icon(), liked.contains(item.id()), item.title(), item.images(), item.content(),
                        item.liked(), item.comments(), item.createTime())).toList();
                blogs = new SectionResult<>(blogs.status(),
                        new ShopPageResult<>(records, page.total(), page.current(), page.size(), page.pages()), blogs.message());
            } catch (RuntimeException failure) {
                org.apache.commons.logging.LogFactory.getLog(getClass()).warn("商详博客点赞状态补齐失败，shopId=" + shopId, failure);
                var page = blogs.data();
                var records = page.records().stream().map(item -> new BlogItem(item.id(), item.shopId(), item.userId(),
                        item.name(), item.icon(), null, item.title(), item.images(), item.content(), item.liked(),
                        item.comments(), item.createTime())).toList();
                blogs = new SectionResult<>(blogs.status(),
                        new ShopPageResult<>(records, page.total(), page.current(), page.size(), page.pages()), blogs.message());
            }
        } else if (viewerId == null && blogs != null && blogs.data() != null) {
            var page = blogs.data();
            var records = page.records().stream().map(item -> new BlogItem(item.id(), item.shopId(), item.userId(),
                    item.name(), item.icon(), false, item.title(), item.images(), item.content(), item.liked(),
                    item.comments(), item.createTime())).toList();
            blogs = new SectionResult<>(blogs.status(),
                    new ShopPageResult<>(records, page.total(), page.current(), page.size(), page.pages()), blogs.message());
        }


        var vouchers = SectionResult.attempt(() -> queries.vouchers(new ShopListPageRequest(shopId, 1, 2)),
                page -> page.records().isEmpty());



        return new ShopDetailResult(snapshot.shop(), vouchers, snapshot.setmeals(), recommendations,
                snapshot.reviews(), snapshot.statistics(), blogs, snapshot.decisionTags(), snapshot.reviewTags(),
                snapshot.relatedShops(), recommendationStateAvailable);
    }

    /**
     * 校验必需任务执行状态与输出，转换为商详用例错误。
     * @param context 当前调用独立使用的任务上下文
     */
    private void ensureSuccess(ShopDetailTaskContext context) {
        if (!context.isSuccess()) {
            throw failure(context.getShopId(), context.getError());
        }
        if (context.getOutput() == null) throw new ShopDetailException(503, "商详未生成有效结果");
    }

    /**
     * 两种执行路径使用相同的用例异常边界，不暴露底层错误。
     * @param shopId 店铺标识
     * @param error 导致失败的异常；没有异常时为 {@code null}
     * @return 保留已知业务状态或转换为 503、504 的商详异常
     */
    private ShopDetailException failure(Long shopId, Throwable error) {
        org.apache.commons.logging.LogFactory.getLog(getClass()).error("商详必需查询失败，shopId=" + shopId, error);
        if (error instanceof ShopDetailException known) return known;
        return new ShopDetailException(error instanceof TimeoutException ? 504 : 503,
                "商详暂时不可用，请稍后重试", error);
    }
}
