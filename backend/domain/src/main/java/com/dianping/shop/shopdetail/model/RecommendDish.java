package com.dianping.shop.shopdetail.model;

import com.dianping.shop.shopdetail.exception.RecommendDishException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 一家店的一道推荐菜；推荐人数由不同用户的推荐关系维护。
 *
 * @param id 记录标识
 * @param shopId 店铺标识
 * @param userId 服务端确认的用户标识
 * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
 * @param name 名称
 * @param coverImage 已上传的封面图片路径
 * @param recommendCount 不同用户的推荐人数
 * @param createTime 创建时间
 */
public record RecommendDish(Long id, Long shopId, Long userId, String requestId,
                            String name, String coverImage, long recommendCount, LocalDateTime createTime) {
    /**
     * 允许引用的已上传业务图片路径规则。
     */
    private static final Pattern UPLOADED_IMAGE = Pattern.compile(
            "/imgs/blogs/[0-9a-f]/[0-9a-f]/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}[.](jpg|jpeg|png|gif)");

    /**
     * 创建并校验RecommendDish，拒绝不满足业务约束的数据。
     *
     * @param id 记录标识
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
     * @param name 名称
     * @param coverImage 已上传的封面图片路径
     * @param recommendCount 不同用户的推荐人数
     * @param createTime 创建时间
     */
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

    /**
     * 比较规范化后的业务提交内容，用于判断同提交标识能否安全重放。
     *
     * @param other 用于比较的另一个业务对象
     * @return 两次提交的规范化业务内容是否相同
     */
    public boolean sameSubmission(RecommendDish other) {
        return shopId.equals(other.shopId) && name.equals(other.name) && coverImage.equals(other.coverImage);
    }

    /**
     * 抛出当前业务对应的非法请求异常。
     *
     * @param message 对外展示的固定提示
     */
    private static void invalid(String message) { throw new RecommendDishException(400, message); }
}
