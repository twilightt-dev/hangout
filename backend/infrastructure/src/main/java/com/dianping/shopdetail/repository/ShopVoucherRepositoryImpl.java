package com.dianping.shopdetail.repository;
import com.dianping.shopdetail.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

/** 普通券与秒杀券共用稳定排序，库存与时间只用于展示状态。 */
@Repository
public class ShopVoucherRepositoryImpl implements ShopVoucherRepository {
    private final JdbcTemplate jdbc;
    public ShopVoucherRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }
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
