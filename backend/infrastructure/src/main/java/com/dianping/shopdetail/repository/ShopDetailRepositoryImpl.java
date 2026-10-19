package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.enums.ReviewTag;
import com.dianping.shopdetail.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 商详读取直接映射为领域数据，不调用遗留 Service 或请求线程身份。
 */
@Repository
public class ShopDetailRepositoryImpl implements ShopDetailRepository {
    /**
     * 关系数据库查询模板。
     */
    private final JdbcTemplate jdbc;
    /**
     * 博客点赞集合使用的 Redis 模板。
     */
    private final StringRedisTemplate redis;
    /**
     * 创建 ShopDetailRepositoryImpl，初始化所需依赖及实例状态。
     *
     * @param jdbc 关系数据库查询模板
     * @param redis 博客点赞集合使用的 Redis 模板
     */
    public ShopDetailRepositoryImpl(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc; this.redis = redis;
    }
    /**
     * 基础店铺资料使用的数据库列集合。
     */
    private static final String BASE_COLUMNS = "id,name,type_id,images,area,address,x,y,avg_price,open_hours";
    /**
     * 基础资料的数据库行映射器。
     */
    private static final RowMapper<ShopBaseInfo> BASE = (r, n) -> new ShopBaseInfo(
            r.getLong("id"), r.getString("name"), r.getObject("type_id", Long.class),
            r.getString("images"), r.getString("area"), r.getString("address"),
            r.getObject("x", Double.class), r.getObject("y", Double.class),
            r.getObject("avg_price", Long.class), r.getString("open_hours"));

    /**
     * 读取店铺自身基础资料，不使用旧缓存中的评价统计。
     *
     * @param id 记录标识
     * @return 店铺基本资料；不存在时返回空 Optional
     */
    public Optional<ShopBaseInfo> findShop(Long id) {
        return jdbc.query("SELECT " + BASE_COLUMNS + " FROM tb_shop WHERE id=?", BASE, id).stream().findFirst();
    }
    /**
     * 读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 探店笔记及当前浏览者点赞状态的分页结果，包含总数与当前页记录
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public ShopPage<ShopVisitBlog> blogs(Long shopId, long current, int size, Long viewerId) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM tb_blog WHERE shop_id=?", Long.class, shopId);
        List<ShopVisitBlog> rows = jdbc.query("""
                SELECT b.*,u.nick_name AS user_name,u.icon AS user_icon
                FROM tb_blog b LEFT JOIN tb_user u ON b.user_id=u.id
                WHERE b.shop_id=? ORDER BY b.liked DESC,b.create_time DESC,b.id DESC LIMIT ? OFFSET ?
                """, (r, n) -> {
            var blog = new ShopVisitBlog();
            blog.setId(r.getLong("id")); blog.setShopId(r.getLong("shop_id")); blog.setUserId(r.getLong("user_id"));
            blog.setUserName(Objects.requireNonNullElse(r.getString("user_name"), "用户"));
            blog.setUserIcon(r.getString("user_icon")); blog.setTitle(r.getString("title"));
            blog.setImages(r.getString("images"));
            String content = r.getString("content");
            blog.setContent(content == null ? "" : content.replaceAll("<[^>]*>", " ").substring(0,
                    Math.min(120, content.replaceAll("<[^>]*>", " ").length())));
            blog.setLiked(r.getInt("liked")); blog.setComments(r.getInt("comments"));
            blog.setCreateTime(time(r, "create_time")); blog.setUpdateTime(time(r, "update_time"));
            blog.setIsLike(false);
            return blog;
        }, shopId, size, Math.multiplyExact(current - 1, (long) size));
        if (viewerId != null && !rows.isEmpty()) {
            try {
                for (var row : rows)
                    row.setIsLike(redis.opsForSet().isMember(com.dianping.constant.RedisConstants.BLOG_LIKE_KEY + row.getId(), viewerId.toString()));
            } catch (RuntimeException error) {
                // 点赞状态不可用不隐藏公开博客；null 由响应明确表达为未知。
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("商详博客点赞状态读取失败，shopId={}", shopId, error);
                rows.forEach(row -> row.setIsLike(null));
            }
        }
        return new ShopPage<>(rows, total, current, size);
    }
    /**
     * 读取时间窗口内的近期评价标签语料，不受首屏预览数量限制。
     *
     * @param shopId 店铺标识
     * @param since 近期统计窗口的起始时间
     * @param limit 最多读取的语料条数
     * @return 近期评价的作者与标签证据列表
     */
    public List<TagEvidence> reviewEvidence(Long shopId, LocalDateTime since, int limit) {
        return jdbc.query("""
                SELECT user_id,tags FROM tb_shop_review WHERE shop_id=? AND create_time>=?
                ORDER BY create_time DESC,id DESC LIMIT ?
                """, (r, n) -> {
            String value = r.getString("tags");
            var tags = value == null ? List.<ReviewTag>of() : Arrays.stream(value.replace("[", "")
                    .replace("]", "").replace("\"", "").split(",")).map(String::trim)
                    .filter(ReviewTag::contains).map(ReviewTag::valueOf).toList();
            return new TagEvidence(r.getLong("user_id"), tags, null);
        }, shopId, since, limit);
    }
    /**
     * 读取时间窗口内的近期探店博客语料，不受首屏预览数量限制。
     *
     * @param shopId 店铺标识
     * @param since 近期统计窗口的起始时间
     * @param limit 最多读取的语料条数
     * @return 近期探店笔记的作者与文本证据列表
     */
    public List<TagEvidence> blogEvidence(Long shopId, LocalDateTime since, int limit) {
        return jdbc.query("""
                SELECT user_id,title,content FROM tb_blog WHERE shop_id=? AND create_time>=?
                ORDER BY create_time DESC,id DESC LIMIT ?
                """, (r, n) -> new TagEvidence(r.getLong("user_id"), List.of(),
                Objects.requireNonNullElse(r.getString("title"), "") + "。" + Objects.requireNonNullElse(r.getString("content"), "")),
                shopId, since, limit);
    }
    /**
     * 以当前店铺为中心读取五公里内同类型店铺，排除自身并按距离、标识排序。
     *
     * @param center 作为周边推荐中心的店铺资料
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 带可用状态的周边分页结果
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public ShopPage<ShopRelatedShop> related(ShopBaseInfo center, long current, int size) {
        // 先按纬度裁剪，再用球面距离筛选，兼容跨日期变更线和高纬度。
        String candidates = """
                SELECT id,name,area,avg_price,score,
                12742017.6 * ASIN(SQRT(LEAST(1.0,POWER(SIN(RADIANS(y-?)/2),2)
                +COS(RADIANS(?))*COS(RADIANS(y))*POWER(SIN(RADIANS(x-?)/2),2)))) AS distance
                FROM tb_shop WHERE type_id=? AND id<>? AND x BETWEEN -180 AND 180
                AND y BETWEEN -90 AND 90 AND y BETWEEN ? AND ?
                """;
        Object[] args = {center.y(), center.y(), center.x(), center.typeId(), center.id(),
                center.y() - 0.045, center.y() + 0.045};
        String nearby = "SELECT * FROM (" + candidates + ") candidate WHERE distance<=5000";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + nearby + ") matches", Long.class, args);
        var pageArgs = new ArrayList<>(Arrays.asList(args));
        pageArgs.add(size); pageArgs.add(Math.multiplyExact(current - 1, (long) size));
        var rows = jdbc.query(nearby + " ORDER BY distance ASC,id ASC LIMIT ? OFFSET ?", (r, n) ->
                new ShopRelatedShop(r.getLong("id"), r.getString("name"), r.getString("area"),
                        r.getObject("avg_price", Long.class), r.getObject("score", Integer.class),
                        r.getDouble("distance"), "附近同类店"), pageArgs.toArray());
        return new ShopPage<>(rows, total, current, size);
    }
    /**
     * 读取可空的数据库时间列并转换为本地日期时间。
     *
     * @param r 待转换的数据库或领域记录
     * @param field 待校验的字段名
     * @return 本地日期时间；数据库列为空时返回 null
     * @throws SQLException 读取数据库时间列失败
     */
    private static LocalDateTime time(ResultSet r, String field) throws SQLException {
        var timestamp = r.getTimestamp(field); return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
