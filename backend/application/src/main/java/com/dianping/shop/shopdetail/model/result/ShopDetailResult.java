package com.dianping.shop.shopdetail.model.result;

import com.dianping.shop.recommend.model.result.RecommendDishItem;
import com.dianping.shop.review.model.result.ShopReviewItem;
import com.dianping.shop.setmeal.model.result.ShopSetmealResult;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shopdetail.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 商详应用响应；领域组合与引擎诊断不直接作为 HTTP 返回类型。
 *
 * @param shop 店铺基础资料
 * @param vouchers 优惠券读取结果
 * @param setmeals 套餐读取结果
 * @param recommendations 推荐菜读取结果
 * @param reviews 评价读取结果
 * @param statistics 全店评价统计响应
 * @param blogs 关联当前店铺的探店笔记
 * @param decisionTags 评价与博客合并得到的近期口碑标签
 * @param reviewTags 仅由近期评价得到的高频标签
 * @param relatedShops 同类型周边店铺
 * @param recommendationStateAvailable 当前用户的推荐状态是否读取成功
 */
public record ShopDetailResult(BaseInfo shop,
        SectionResult<ShopPageResult<VoucherItem>> vouchers,
        SectionResult<ShopPageResult<ShopSetmealResult>> setmeals,
        SectionResult<ShopPageResult<RecommendDishItem>> recommendations,
        SectionResult<ShopPageResult<ShopReviewItem>> reviews,
        SectionResult<ReviewStatistics> statistics,
        SectionResult<ShopPageResult<BlogItem>> blogs,
        SectionResult<List<TagItem>> decisionTags,
        SectionResult<List<TagItem>> reviewTags,
        SectionResult<ShopPageResult<RelatedItem>> relatedShops,
        boolean recommendationStateAvailable) {
    /**
     * 只转换响应格式，不查询仓储或推导业务规则。
     *
     * @param detail 已经组合好的商详业务数据
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public static ShopDetailResult from(ShopDetailAggregate detail) {
        return from(detail, Map.of());
    }

    /**
     * 把领域组合与应用执行状态转换为六区响应，不查询仓储或推导业务规则。
     *
     * @param detail 已经组合好的商详业务数据
     * @param executionResults 任务上下文记录的各分项状态
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public static ShopDetailResult from(ShopDetailAggregate detail, Map<String, SectionResult<?>> executionResults) {
        var states = new HashMap<>(executionResults);
        if (detail.getRelatedUnavailableReason() != null)
            states.putIfAbsent("relatedShops", SectionResult.unavailable("UNAVAILABLE", detail.getRelatedUnavailableReason()));
        var recommended = detail.getRecommendedIds() == null ? Set.<Long>of() : detail.getRecommendedIds();
        var personal = states.get("recommendationState");
        return new ShopDetailResult(BaseInfo.from(detail.getShop()),
                section(states, "vouchers", detail.getVouchers(), page -> page.records().isEmpty(),
                        page -> ShopPageResult.from(page, VoucherItem::from)),
                section(states, "setmeals", detail.getSetmeals(), page -> page.records().isEmpty(),
                        page -> ShopPageResult.from(page, ShopSetmealResult::from)),
                section(states, "recommendations", detail.getRecommendations(), page -> page.records().isEmpty(), page -> ShopPageResult.from(page,
                        dish -> RecommendDishItem.from(dish, recommended.contains(dish.id())))),
                section(states, "reviews", detail.getReviews(), page -> page.records().isEmpty(),
                        page -> ShopPageResult.from(page, ShopReviewItem::from)),
                section(states, "statistics", detail.getReviewSummary(), summary -> summary.totalReviews() == 0, ReviewStatistics::from),
                section(states, "blogs", detail.getBlogs(), page -> page.records().isEmpty(),
                        page -> ShopPageResult.from(page, BlogItem::from)),
                section(states, "decisionTags", detail.getDecisionTags(), List::isEmpty, tags -> tags.stream().map(TagItem::from).toList()),
                section(states, "reviewTags", detail.getReviewTags(), List::isEmpty, tags -> tags.stream().map(TagItem::from).toList()),
                section(states, "relatedShops", detail.getRelatedShops(), page -> page.records().isEmpty(),
                        page -> ShopPageResult.from(page, RelatedItem::from)),
                personal == null ? detail.getRecommendedIds() != null : personal.available());
    }

    /**
     * 合并业务数据与应用状态，再转换为前端分项响应。
     *
     * @param <T> 当前业务数据类型
     * @param <R> 转换后的业务数据类型
     * @param states 按分项名称组织的应用执行状态
     * @param key 上下文属性名或区域名称
     * @param data 业务数据
     * @param empty 判断业务数据是否为空的规则或判断结果
     * @param mapper 数据映射器
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    private static <T, R> SectionResult<R> section(Map<String, SectionResult<?>> states, String key,
            T data, Predicate<T> empty, Function<T, R> mapper) {
        var state = states.get(key);
        if (state != null && !state.available()) return SectionResult.unavailable(state.status(), state.message());
        if (data == null) return SectionResult.unavailable("FAILED", "暂时不可用，请重试");
        if (state != null) return new SectionResult<>(state.status(), mapper.apply(data), state.message());
        return SectionResult.success(mapper.apply(data), empty.test(data));
    }
    /**
     * 商详基本信息区的店铺自身资料响应。
     *
     * @param id 记录标识
     * @param name 名称
     * @param typeId 店铺类型标识
     * @param images 图片路径集合的存储文本
     * @param area 所属商圈
     * @param address 详细地址
     * @param x 经度
     * @param y 纬度
     * @param avgPrice 店铺人均价格
     * @param openHours 营业时间
     */
    public record BaseInfo(Long id, String name, Long typeId, String images, String area, String address,
                           Double x, Double y, Long avgPrice, String openHours) {
        /**
         * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
         *
         * @param s 待转换的业务数据
         * @return 转换或执行后的业务响应，保留对应分项状态
         */
        public static BaseInfo from(ShopBaseInfo s) { return new BaseInfo(s.id(),s.name(),s.typeId(),s.images(),s.area(),s.address(),s.x(),s.y(),s.avgPrice(),s.openHours()); }
    }
    /**
     * 优惠券展示响应；展示状态不替代交易接口的最终资格校验。
     *
     * @param id 记录标识
     * @param shopId 店铺标识
     * @param title 标题
     * @param subTitle 副标题
     * @param rules 使用规则
     * @param payValue 支付金额，单位为分
     * @param actualValue 抵扣金额，单位为分
     * @param type 优惠券类型
     * @param stock 秒杀券剩余库存
     * @param beginTime 秒杀开始时间
     * @param endTime 秒杀结束时间
     * @param availability 可购买、未开始或售罄的展示状态
     */
    public record VoucherItem(Long id, Long shopId, String title, String subTitle, String rules, Long payValue,
            Long actualValue, Integer type, Integer stock, LocalDateTime beginTime, LocalDateTime endTime, String availability) {
        /**
         * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
         *
         * @param v 优惠券业务数据
         * @return 转换或执行后的业务响应，保留对应分项状态
         */
        public static VoucherItem from(ShopVoucher v) { return new VoucherItem(v.id(),v.shopId(),v.title(),v.subTitle(),v.rules(),v.payValue(),v.actualValue(),v.type(),v.stock(),v.beginTime(),v.endTime(),v.availability()); }
    }
    /**
     * 全部评价的数量及均分响应，与近期标签语料口径分开。
     *
     * @param totalReviews 全店评价数量
     * @param averageScore 五分制均分；无评价时为 {@code null}
     */
    public record ReviewStatistics(long totalReviews, BigDecimal averageScore) {
        /**
         * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
         *
         * @param s 待转换的业务数据
         * @return 转换或执行后的业务响应，保留对应分项状态
         */
        public static ReviewStatistics from(ShopReviewSummary s) { return new ReviewStatistics(s.totalReviews(),s.averageScore()); }
    }
    /**
     * 探店笔记预览与当前浏览者点赞状态响应。
     * @param id 记录标识
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param name 名称
     * @param icon 用户当前头像路径
     * @param isLike 当前用户是否点赞；读取故障时为 {@code null}
     * @param title 标题
     * @param images 图片路径集合的存储文本
     * @param content 正文内容
     * @param liked 点赞数量
     * @param comments 评价或评论数量
     * @param createTime 创建时间
     */
    public record BlogItem(Long id, Long shopId, Long userId, String name, String icon, Boolean isLike,
            String title, String images, String content, Integer liked, Integer comments, LocalDateTime createTime) {
        /**
         * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
         *
         * @param b 探店笔记业务数据
         * @return 转换或执行后的业务响应，保留对应分项状态
         */
        public static BlogItem from(ShopVisitBlog b) { return new BlogItem(b.getId(),b.getShopId(),b.getUserId(),b.getUserName(),b.getUserIcon(),b.getIsLike(),b.getTitle(),b.getImages(),b.getContent(),b.getLiked(),b.getComments(),b.getCreateTime()); }
    }
    /**
     * 用户口碑标签及去重提及人数响应。
     *
     * @param code 标签代码
     * @param name 名称
     * @param userCount 去重后的提及用户数
     */
    public record TagItem(String code, String name, long userCount) {
        /**
         * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
         *
         * @param t 标签业务数据
         * @return 转换或执行后的业务响应，保留对应分项状态
         */
        public static TagItem from(ShopDecisionTag t) { return new TagItem(t.code(),t.name(),t.userCount()); }
    }
    /**
     * 以当前店铺为中心的同类周边店铺响应。
     * @param id 记录标识
     * @param name 名称
     * @param area 所属商圈
     * @param avgPrice 店铺人均价格
     * @param score 按实际分数的十倍保存的评分
     * @param distance 距当前店铺的球面直线距离，单位为米
     * @param recommendationReason 周边推荐说明
     */
    public record RelatedItem(Long id, String name, String area, Long avgPrice, Integer score, Double distance, String recommendationReason) {
        /**
         * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
         *
         * @param s 待转换的业务数据
         * @return 转换或执行后的业务响应，保留对应分项状态
         */
        public static RelatedItem from(ShopRelatedShop s) { return new RelatedItem(s.getShopId(),s.getShopName(),s.getArea(),s.getAvgPrice(),s.getScore(),s.getDistance(),s.getRecommendationReason()); }
    }
}
