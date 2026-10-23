package com.dianping.shopdetail.repository;
import com.dianping.shop.shopdetail.model.ShopPage;
import com.dianping.shop.shopdetail.model.ShopVoucher;
import com.dianping.shop.shopdetail.repository.ShopVoucherRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;

/**
 * 普通券与秒杀券共用稳定排序，库存与时间只用于展示状态。
 */
@Repository
public class ShopVoucherRepositoryImpl implements ShopVoucherRepository {
    /**
     * 关系数据库查询模板。
     */
    private final JdbcTemplate jdbc;
    /**
     * 创建 ShopVoucherRepositoryImpl，初始化所需依赖及实例状态。
     *
     * @param jdbc 关系数据库查询模板
     */
    public ShopVoucherRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param now 本次读取或创建使用的时间基准
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public ShopPage<ShopVoucher> page(Long shopId, long current, int size, LocalDateTime now) {
        String from = """
                FROM tb_voucher v LEFT JOIN tb_seckill_voucher s ON s.voucher_id=v.id
                WHERE v.shop_id=? AND v.status=1
                AND (v.type=0 OR (v.type=1 AND s.begin_time IS NOT NULL AND s.end_time>?))
                """;
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, shopId, now);
        var rows = jdbc.query("""
                SELECT v.*,s.stock,s.begin_time,s.end_time,
                CASE WHEN v.type=0 OR (s.begin_time<=? AND s.stock>0) THEN 0 ELSE 1 END AS unavailable
                """ + from + " ORDER BY unavailable ASC,v.pay_value ASC,v.id ASC LIMIT ? OFFSET ?",
                (r, n) -> {
                    var begin = r.getTimestamp("begin_time");
                    var end = r.getTimestamp("end_time");
                    Integer stock = r.getObject("stock", Integer.class);
                    String state = r.getInt("type") == 0 ? "AVAILABLE"
                            : begin.toLocalDateTime().isAfter(now) ? "UPCOMING"
                            : stock == null || stock <= 0 ? "SOLD_OUT" : "AVAILABLE";
                    return new ShopVoucher(r.getLong("id"), r.getLong("shop_id"), r.getString("title"),
                            r.getString("sub_title"), r.getString("rules"), r.getLong("pay_value"),
                            r.getLong("actual_value"), r.getInt("type"), stock,
                            begin == null ? null : begin.toLocalDateTime(), end == null ? null : end.toLocalDateTime(), state);
                }, now, shopId, now, size, Math.multiplyExact(current - 1, (long) size));
        return new ShopPage<>(rows, total, current, size);
    }
}
