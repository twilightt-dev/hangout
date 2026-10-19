package com.dianping.shopdetail;

import com.dianping.shopdetail.enums.ReviewTag;
import com.dianping.shopdetail.model.TagEvidence;
import com.dianping.shopdetail.service.ShopDetailDomainService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** 通过领域服务入口验证用户口碑规则，不依赖任务节点实现。 */
class ShopDetailDomainServiceTest {
    private final ShopDetailDomainService service = new ShopDetailDomainService();

    @Test
    void questionsDoNotContributePositiveEvidence() {
        var tags = service.mergeDecisionTags(List.of(), List.of(
                new TagEvidence(1L, List.of(), "好吃吗？停车方便吗？"),
                new TagEvidence(2L, List.of(), "好吃吗?停车方便吗?")));
        assertThat(tags).isEmpty();
    }

    @Test
    void countsDistinctAuthorsAcrossReviewsAndBlogsAndRejectsNegativeSentences() {
        var tags = service.mergeDecisionTags(List.of(
                new TagEvidence(1L, List.of(ReviewTag.TASTY), null),
                new TagEvidence(1L, List.of(ReviewTag.TASTY), null),
                new TagEvidence(2L, List.of(ReviewTag.NICE_ENVIRONMENT), null)), List.of(
                new TagEvidence(1L, List.of(), "味道好，环境好"),
                new TagEvidence(3L, List.of(), "味道好。服务不热情，停车不方便。"),
                new TagEvidence(4L, List.of(), "味道不好。价格并不实惠。")));
        assertThat(tags).extracting(tag -> tag.code()).containsExactly("TASTY", "NICE_ENVIRONMENT");
        assertThat(tags).extracting(tag -> tag.userCount()).containsExactly(2L, 2L);
    }
}
