package com.dianping.shop.shopdetail.service;

import com.dianping.shop.shopdetail.enums.ReviewTag;
import com.dianping.shop.shopdetail.model.*;
import com.dianping.shop.shopdetail.repository.*;
import com.dianping.shop.shopdetail.exception.ShopDetailException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 商详联合查询与跨对象业务规则；不依赖任务引擎、HTTP 或持久化框架。
 */
public class ShopDetailDomainService {
    /**
     * 首屏优惠券数量上限。
     */
    public static final int VOUCHER_PREVIEW_SIZE = 2;
    /**
     * 首屏套餐数量上限。
     */
    public static final int SETMEAL_PREVIEW_SIZE = 4;
    /**
     * 首屏推荐菜数量上限。
     */
    public static final int DISH_PREVIEW_SIZE = 6;
    /**
     * 首屏评价数量上限。
     */
    public static final int REVIEW_PREVIEW_SIZE = 3;
    /**
     * 首屏探店笔记数量上限。
     */
    public static final int BLOG_PREVIEW_SIZE = 4;
    /**
     * 店铺基础资料、标签语料和周边查询仓储。
     */
    private final ShopDetailRepository details;
    /**
     * 优惠券仓储。
     */
    private final ShopVoucherRepository vouchers;
    /**
     * 店铺套餐仓储。
     */
    private final ShopSetmealRepository meals;
    /**
     * 店铺评价仓储。
     */
    private final ShopReviewRepository reviews;
    /**
     * 推荐菜与用户推荐关系仓储。
     */
    private final RecommendDishRepository dishes;

    /**
     * 创建 ShopDetailDomainService，初始化所需依赖及实例状态。
     *
     * @param details 店铺基础资料、探店语料和周边查询仓储
     * @param vouchers 优惠券仓储
     * @param meals 套餐仓储
     * @param reviews 评价仓储
     * @param dishes 推荐菜及用户推荐关系仓储
     */
    public ShopDetailDomainService(ShopDetailRepository details, ShopVoucherRepository vouchers,
            ShopSetmealRepository meals, ShopReviewRepository reviews, RecommendDishRepository dishes) {
        this.details = Objects.requireNonNull(details);
        this.vouchers = Objects.requireNonNull(vouchers);
        this.meals = Objects.requireNonNull(meals);
        this.reviews = Objects.requireNonNull(reviews);
        this.dishes = Objects.requireNonNull(dishes);
    }

    /**
     * 同步联合查询；直接组合仓储结果，查询异常交由调用方处理。
     *
     * @param shopId 店铺标识
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 包含查询事实及衍生数据的商详组合结果
     */
    public ShopDetailAggregate getShopDetail(Long shopId, Long viewerId) {
        return getShopDetail(shopId, viewerId, true);
    }

    /**
     * 串行查询商详快照
     * @param shopId 店铺标识
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @param includeVouchers 是否在本次聚合中读取优惠券
     * @return 包含查询事实及衍生数据的商详组合结果
     */
    public ShopDetailAggregate getShopDetail(Long shopId, Long viewerId, boolean includeVouchers) {
        ShopDetailAggregate aggregate = new ShopDetailAggregate();
        aggregate.setShop(base(shopId));
        aggregate.setVouchers(includeVouchers
                ? vouchers.page(shopId, 1, VOUCHER_PREVIEW_SIZE, LocalDateTime.now()) : null);
        aggregate.setSetmeals(meals.pageWithTotal(shopId, 1, SETMEAL_PREVIEW_SIZE));
        aggregate.setRecommendations(dishes.pageWithTotal(shopId, 1, DISH_PREVIEW_SIZE));
        aggregate.setRecommendedIds(recommendedIds(viewerId, aggregate.getRecommendations()));
        aggregate.setReviews(reviews.pageWithTotal(shopId, 1, REVIEW_PREVIEW_SIZE));
        aggregate.setReviewSummary(reviews.summarize(shopId));
        aggregate.setBlogs(details.blogs(shopId, 1, BLOG_PREVIEW_SIZE, viewerId));
        return enrichShopDetail(aggregate, LocalDateTime.now().minusDays(90));
    }

    /**
     * 以游客身份同步聚合店铺详情，复用包含浏览者参数的领域查询入口。
     *
     * @param shopId 店铺标识
     * @return 包含查询事实及衍生数据的商详组合结果
     */
    public ShopDetailAggregate getShopDetail(Long shopId) { return getShopDetail(shopId, null); }

    /**
     * 校验店铺标识并查询基本资料；店铺不存在时按业务错误返回。
     *
     * @param shopId 店铺标识
     * @return 当前店铺的基本资料
     * @throws ShopDetailException 店铺标识非法时为 400，店铺不存在时为 404
     */
    public ShopBaseInfo base(Long shopId) {
        if (shopId == null || shopId <= 0) throw new ShopDetailException(400, "店铺 ID 必须为正整数");
        return details.findShop(shopId).orElseThrow(() -> new ShopDetailException(404, "店铺不存在"));
    }

    /**
     * 取得全部店铺标识，供定时预热逐店构建公共商详快照。
     *
     * @return 当前全部店铺标识
     */
    public List<Long> allShopIds() { return details.allShopIds(); }

    /**
     * 批量读取浏览者已点赞的博客标识。
     *
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @param blogIds 待查询的博客标识
     * @return 当前浏览者已点赞的博客标识
     */
    public Set<Long> likedBlogIds(Long viewerId, List<Long> blogIds) {
        if (viewerId == null || blogIds.isEmpty()) return Set.of();
        return Set.copyOf(details.likedBlogIds(viewerId, blogIds));
    }

    /**
     * 读取优惠券，沿用上架、有效期和可购买优先的展示规则。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 优惠券的分页结果，包含总数与当前页记录
     */
    public ShopPage<ShopVoucher> vouchers(Long shopId, long current, int size) {
        return vouchers.page(shopId, current, size, LocalDateTime.now());
    }

    /**
     * 读取当前店铺的上架套餐及固定菜品明细。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 上架套餐及固定菜品明细的分页结果，包含总数与当前页记录
     */
    public ShopPage<ShopSetmeal> setmeals(Long shopId, long current, int size) {
        return meals.pageWithTotal(shopId, current, size);
    }

    /**
     * 读取按推荐人数、创建时间及标识排列的店内推荐菜。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 推荐菜的分页结果，包含总数与当前页记录
     */
    public ShopPage<RecommendDish> recommendations(Long shopId, long current, int size) {
        return dishes.pageWithTotal(shopId, current, size);
    }

    /**
     * 批量读取当前用户已推荐的菜品标识。
     *
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @param page 需要补齐状态的推荐菜分页数据
     * @return 当前用户在给定菜品集合中已推荐的标识
     */
    public Set<Long> recommendedIds(Long viewerId, ShopPage<RecommendDish> page) {
        if (viewerId == null || page.records().isEmpty()) return Set.of();
        return Set.copyOf(dishes.recommendedIds(viewerId, page.records().stream().map(RecommendDish::id).toList()));
    }

    /**
     * 批量读取当前浏览者已推荐的菜品标识。
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @param dishIds 待查询的菜品标识
     * @return 当前浏览者已推荐的菜品标识
     */
    public Set<Long> recommendedIds(Long viewerId, List<Long> dishIds) {
        if (viewerId == null || dishIds.isEmpty()) return Set.of();
        return Set.copyOf(dishes.recommendedIds(viewerId, dishIds));
    }

    /**
     * 读取店铺评价及当前作者资料。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 店铺评价及当前作者资料的分页结果，包含总数与当前页记录
     */
    public ShopPage<ShopReview> reviews(Long shopId, long current, int size) {
        return reviews.pageWithTotal(shopId, current, size);
    }

    /**
     * 读取全部评价的数量和总体评分均分。
     *
     * @param shopId 店铺标识
     * @return 全店评价数量和五分制总体均分
     */
    public ShopReviewSummary statistics(Long shopId) { return reviews.summarize(shopId); }

    /**
     * 读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 探店笔记及当前浏览者点赞状态的分页结果，包含总数与当前页记录
     */
    public ShopPage<ShopVisitBlog> blogs(Long shopId, long current, int size, Long viewerId) {
        return details.blogs(shopId, current, size, viewerId);
    }

    /**
     * 根据评价、博客及店铺位置补齐衍生数据。
     *
     * @param aggregate 待补齐衍生数据的商详组合对象
     * @param since 近期统计窗口的起始时间
     * @return 包含查询事实及衍生数据的商详组合结果
     */
    public ShopDetailAggregate enrichShopDetail(ShopDetailAggregate aggregate, LocalDateTime since) {
        Long shopId = aggregate.getShop().id();
        var reviewEvidence = reviewEvidence(shopId, since);
        var blogEvidence = blogEvidence(shopId, since);
        aggregate.setDecisionTags(mergeDecisionTags(reviewEvidence, blogEvidence));
        aggregate.setReviewTags(mergeDecisionTags(reviewEvidence, List.of()));
        aggregate.setRelatedUnavailableReason(relatedUnavailableReason(aggregate.getShop()));
        aggregate.setRelatedShops(related(aggregate.getShop(), 1, 6));
        return aggregate;
    }

    /**
     * 读取时间窗口内的近期评价标签语料，不受首屏预览数量限制。
     *
     * @param shopId 店铺标识
     * @param since 近期统计窗口的起始时间
     * @return 近期评价的作者与标签证据列表
     */
    public List<TagEvidence> reviewEvidence(Long shopId, LocalDateTime since) {
        return details.reviewEvidence(shopId, since, 200);
    }

    /**
     * 读取时间窗口内的近期探店博客语料，不受首屏预览数量限制。
     *
     * @param shopId 店铺标识
     * @param since 近期统计窗口的起始时间
     * @return 近期探店笔记的作者与文本证据列表
     */
    public List<TagEvidence> blogEvidence(Long shopId, LocalDateTime since) {
        return details.blogEvidence(shopId, since, 50);
    }

    /**
     * 以当前店铺为中心读取五公里内同类型店铺，排除自身并按距离、标识排序。
     *
     * @param center 作为周边推荐中心的店铺资料
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 按距离排序的分页结果；缺少坐标或类型时领域入口返回 {@code null}
     */
    public ShopPage<ShopRelatedShop> related(ShopBaseInfo center, long current, int size) {
        return relatedUnavailableReason(center) == null ? details.related(center, current, size) : null;
    }

    /**
     * 判断店铺是否具备周边推荐所需的有效坐标和类型。
     *
     * @param center 作为周边推荐中心的店铺资料
     * @return 无法推荐的业务原因；具备查询条件时为 {@code null}
     */
    public String relatedUnavailableReason(ShopBaseInfo center) {
        if (!hasCoordinates(center)) return "缺少位置信息，暂无法推荐周边店铺";
        if (center.typeId() == null) return "缺少店铺类型，暂无法推荐周边店铺";
        return null;
    }

    /**
     * 用于分割口碑语料句子的规则。
     */
    private static final Pattern SENTENCE = Pattern.compile("(?<=[。！？!?；;，,\\n\\r])");
    /**
     * 用于排除否定和不确定表述的词语规则。
     */
    private static final Pattern UNCERTAIN = Pattern.compile("不|没|无|未|差|难|慢|脏|贵|糟|一般|但是|不过|然而|听说|据说|可能|似乎|希望|要是|如果|据称|未必|算不上");
    /**
     * 预设口碑标签对应的正向文本短语词典。
     */
    private static final Map<ReviewTag, List<String>> PHRASES = Map.of(
            ReviewTag.TASTY, List.of("味道好", "很好吃", "好吃", "味道不错"),
            ReviewTag.NICE_ENVIRONMENT, List.of("环境好", "环境优美", "环境舒适"),
            ReviewTag.FRIENDLY_SERVICE, List.of("服务热情", "服务很好", "服务周到"),
            ReviewTag.GOOD_VALUE, List.of("价格实惠", "性价比高", "价格便宜"),
            ReviewTag.CLEAN, List.of("干净卫生", "很干净", "卫生很好"),
            ReviewTag.CONVENIENT_LOCATION, List.of("交通方便", "交通便利"),
            ReviewTag.FAST_SERVICE, List.of("出餐快", "上菜快"),
            ReviewTag.EASY_PARKING, List.of("停车方便", "停车便利"));

    /**
     * 按作者去重合并评价标签与明确正向的博客表述，筛选至少两名作者提及的前五项。
     *
     * @param reviews 近期评价标签证据
     * @param blogs 近期探店博客文本证据
     * @return 按提及用户数和词典顺序排列的最多五个口碑标签
     */
    public List<ShopDecisionTag> mergeDecisionTags(List<TagEvidence> reviews, List<TagEvidence> blogs) {
        var users = new EnumMap<ReviewTag, Set<Long>>(ReviewTag.class);
        for (var evidence : reviews) {
            if (evidence.userId() == null) continue;
            for (var tag : evidence.tags()) users.computeIfAbsent(tag, key -> new HashSet<>()).add(evidence.userId());
        }
        for (var evidence : blogs) {
            if (evidence.userId() == null || evidence.text() == null) continue;
            String plain = evidence.text().replaceAll("<[^>]*>", " ");
            for (String sentence : SENTENCE.split(plain)) {
                if (sentence.contains("?") || sentence.contains("？") || sentence.contains("吗") || sentence.contains("是否")
                        || UNCERTAIN.matcher(sentence.replace("不错", "很好")).find()) continue;
                for (var tag : ReviewTag.values()) {
                    if (PHRASES.get(tag).stream().anyMatch(sentence::contains))
                        users.computeIfAbsent(tag, key -> new HashSet<>()).add(evidence.userId());
                }
            }
        }
        return Arrays.stream(ReviewTag.values())
                .filter(tag -> users.getOrDefault(tag, Set.of()).size() >= 2)
                .sorted(Comparator.<ReviewTag>comparingInt(tag -> users.get(tag).size()).reversed()
                        .thenComparingInt(Enum::ordinal))
                .limit(5).map(tag -> new ShopDecisionTag(tag.name(), tag.displayName(), users.get(tag).size())).toList();
    }

    /**
     * 判断店铺经纬度是否非空、有限且处于地理坐标范围内。
     *
     * @param shop 店铺基础资料
     * @return 是否具备有效经纬度
     */
    public boolean hasCoordinates(ShopBaseInfo shop) {
        return shop != null && shop.x() != null && shop.y() != null
                && Double.isFinite(shop.x()) && Double.isFinite(shop.y())
                && shop.x() >= -180 && shop.x() <= 180 && shop.y() >= -90 && shop.y() <= 90;
    }

    /**
     * 球面直线距离，米；周边展示和范围筛选共用。
     *
     * @param x1 起点经度
     * @param y1 起点纬度
     * @param x2 终点经度
     * @param y2 终点纬度
     * @return 两点直线距离，单位为米
     */
    public double distanceMeters(double x1, double y1, double x2, double y2) {
        double dy = Math.toRadians(y2 - y1), dx = Math.toRadians(x2 - x1);
        double a = Math.pow(Math.sin(dy / 2), 2) + Math.cos(Math.toRadians(y1))
                * Math.cos(Math.toRadians(y2)) * Math.pow(Math.sin(dx / 2), 2);
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
