package com.dianping.shopdetail.repository;
import com.dianping.shopdetail.model.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/**
 * 商详基础、探店、语料和周边的读取边界，不依赖旧业务 Service。
 */
public interface ShopDetailRepository {
    /**
     * 按店铺标识升序取得当前全部店铺 ID，供后台商详快照预热使用。
     *
     * @return 当前全部店铺标识
     */
    List<Long> allShopIds();
    /**
     * 读取店铺自身基础资料，不使用旧缓存中的评价统计。
     *
     * @param shopId 店铺标识
     * @return 店铺基本资料；不存在时返回空 Optional
     */
    Optional<ShopBaseInfo> findShop(Long shopId);
    /**
     * 读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 探店笔记及当前浏览者点赞状态的分页结果，包含总数与当前页记录
     */
    ShopPage<ShopVisitBlog> blogs(Long shopId, long current, int size, Long viewerId);
    /**
     * 读取时间窗口内的近期评价标签语料，不受首屏预览数量限制。
     *
     * @param shopId 店铺标识
     * @param since 近期统计窗口的起始时间
     * @param limit 最多读取的语料条数
     * @return 近期评价的作者与标签证据列表
     */
    List<TagEvidence> reviewEvidence(Long shopId, LocalDateTime since, int limit);
    /**
     * 读取时间窗口内的近期探店博客语料，不受首屏预览数量限制。
     *
     * @param shopId 店铺标识
     * @param since 近期统计窗口的起始时间
     * @param limit 最多读取的语料条数
     * @return 近期探店笔记的作者与文本证据列表
     */
    List<TagEvidence> blogEvidence(Long shopId, LocalDateTime since, int limit);
    /**
     * 批量读取浏览者已点赞的探店博客标识。
     *
     * @param viewerId 当前浏览者标识
     * @param blogIds 待查询的博客标识
     * @return 当前浏览者已点赞的博客标识
     */
    java.util.Set<Long> likedBlogIds(Long viewerId, List<Long> blogIds);
    /**
     * 以当前店铺为中心读取五公里内同类型店铺，排除自身并按距离、标识排序。
     *
     * @param center 作为周边推荐中心的店铺资料
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 带可用状态的周边分页结果
     */
    ShopPage<ShopRelatedShop> related(ShopBaseInfo center, long current, int size);
}
