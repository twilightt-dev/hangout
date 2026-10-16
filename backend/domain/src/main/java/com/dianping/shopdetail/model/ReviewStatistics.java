package com.dianping.shopdetail.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 由原始总体评分总和计算统计，永远不从已舍入均分反推总分。 */
public record ReviewStatistics(long total, long scoreSum) {
    public ReviewStatistics {
        if (total < 0 || total > Integer.MAX_VALUE || scoreSum < 0)
            throw new IllegalStateException("评价统计超出支持范围");
    }
    public int storedScore() {
        return total == 0 ? 0 : BigDecimal.valueOf(scoreSum)
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).intValueExact();
    }
    public BigDecimal averageScore() { return total == 0 ? null : BigDecimal.valueOf(storedScore(), 1); }
}
