package com.dianping.shopdetail.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * 通过公开入口验证当前模块的业务约束和执行边界。
 */
class ShopReviewTest {
    /**
     * 验证四项独立评分及提交内容的规范化规则。
     */
    @Test
    void preservesIndependentScoresAndCanonicalizesSubmissionContent() {
        var review = ShopReview.create(1L, 7L, "00000000-0000-0000-0000-000000000001",
                45, 30, 40, 50, "  味道很好  ", List.of("TASTY", "CLEAN", "TASTY"), LocalDateTime.now());
        assertThat(review.overallScore()).isEqualTo(45);
        assertThat(review.tasteScore()).isEqualTo(30);
        assertThat(review.content()).isEqualTo("味道很好");
        assertThat(review.tags()).containsExactly("CLEAN", "TASTY");
        assertThatThrownBy(() -> review.tags().add("GOOD_VALUE")).isInstanceOf(UnsupportedOperationException.class);
    }
}
