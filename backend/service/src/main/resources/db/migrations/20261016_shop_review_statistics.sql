-- 统计切换脚本：确认已停止评价写入后执行；只按新评价明细覆盖店铺汇总。
UPDATE tb_shop s
LEFT JOIN (
    SELECT shop_id, COUNT(*) AS total, ROUND(AVG(overall_score), 0) AS score
    FROM tb_shop_review GROUP BY shop_id
) r ON r.shop_id = s.id
SET s.comments = COALESCE(r.total, 0), s.score = COALESCE(r.score, 0);
