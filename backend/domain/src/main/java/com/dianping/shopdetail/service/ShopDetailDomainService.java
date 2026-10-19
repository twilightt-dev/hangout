package com.dianping.shopdetail.service;

import com.dianping.shopdetail.enums.ReviewTag;
import com.dianping.shopdetail.model.*;
import java.util.*;
import java.util.regex.Pattern;

/** 跨评价与博客的口碑规则；不依赖任务引擎、HTTP 或持久化框架。 */
public class ShopDetailDomainService {
    private static final Pattern SENTENCE = Pattern.compile("(?<=[。！？!?；;，,\\n\\r])");
    private static final Pattern UNCERTAIN = Pattern.compile("不|没|无|未|差|难|慢|脏|贵|糟|一般|但是|不过|然而|听说|据说|可能|似乎|希望|要是|如果|据称|未必|算不上");
    private static final Map<ReviewTag, List<String>> PHRASES = Map.of(
            ReviewTag.TASTY, List.of("味道好", "很好吃", "好吃", "味道不错"),
            ReviewTag.NICE_ENVIRONMENT, List.of("环境好", "环境优美", "环境舒适"),
            ReviewTag.FRIENDLY_SERVICE, List.of("服务热情", "服务很好", "服务周到"),
            ReviewTag.GOOD_VALUE, List.of("价格实惠", "性价比高", "价格便宜"),
            ReviewTag.CLEAN, List.of("干净卫生", "很干净", "卫生很好"),
            ReviewTag.CONVENIENT_LOCATION, List.of("交通方便", "交通便利"),
            ReviewTag.FAST_SERVICE, List.of("出餐快", "上菜快"),
            ReviewTag.EASY_PARKING, List.of("停车方便", "停车便利"));

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

    public boolean hasCoordinates(ShopBaseInfo shop) {
        return shop != null && shop.x() != null && shop.y() != null
                && Double.isFinite(shop.x()) && Double.isFinite(shop.y())
                && shop.x() >= -180 && shop.x() <= 180 && shop.y() >= -90 && shop.y() <= 90;
    }

    /** 周边用例规则由领域入口限定，空间筛选与分页由仓储实现。 */
    public ShopPage<ShopRelatedShop> buildRelatedShops(
            com.dianping.shopdetail.repository.ShopDetailRepository repository,
            ShopBaseInfo center, long current, int size) {
        if (!hasCoordinates(center) || center.typeId() == null)
            throw new IllegalArgumentException("周边推荐缺少店铺坐标或类型");
        return repository.related(center, current, size);
    }

    /** 球面直线距离，米；周边展示和范围筛选共用。 */
    public double distanceMeters(double x1, double y1, double x2, double y2) {
        double dy = Math.toRadians(y2 - y1), dx = Math.toRadians(x2 - x1);
        double a = Math.pow(Math.sin(dy / 2), 2) + Math.cos(Math.toRadians(y1))
                * Math.cos(Math.toRadians(y2)) * Math.pow(Math.sin(dx / 2), 2);
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
