package com.dianping.shopdetail.model;

import com.dianping.shopdetail.exception.RecommendDishException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 一家店的一道推荐菜；推荐人数由不同用户的推荐关系维护。 */
public record RecommendDish(Long id, Long shopId, Long userId, String requestId,
                            String name, String coverImage, long recommendCount, LocalDateTime createTime) {
    private static final Pattern UPLOADED_IMAGE = Pattern.compile(
            "/imgs/blogs/[0-9a-f]/[0-9a-f]/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}[.](jpg|jpeg|png|gif)");

    public RecommendDish {
        if (shopId == null || shopId <= 0 || userId == null || userId <= 0) invalid("店铺和作者标识必须为正数");
        if (requestId == null) invalid("提交标识必须是 UUID");
        try {
            String canonical = UUID.fromString(requestId).toString();
            if (!canonical.equalsIgnoreCase(requestId)) invalid("提交标识必须是 UUID");
            requestId = canonical;
        } catch (IllegalArgumentException error) { throw new RecommendDishException(400, "提交标识必须是 UUID"); }
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > 100 || name.codePoints().anyMatch(Character::isISOControl))
            invalid("菜品名称须为 1～100 个字符，不能包含控制字符");
        coverImage = coverImage == null ? "" : coverImage.strip();
        if (!UPLOADED_IMAGE.matcher(coverImage).matches()) invalid("请上传一张菜品封面图片");
        if (recommendCount < 0) throw new IllegalStateException("推荐人数不能为负数");
        Objects.requireNonNull(createTime, "创建时间不得为空");
    }

    public boolean sameSubmission(RecommendDish other) {
        return shopId.equals(other.shopId) && name.equals(other.name) && coverImage.equals(other.coverImage);
    }

    private static void invalid(String message) { throw new RecommendDishException(400, message); }
}
