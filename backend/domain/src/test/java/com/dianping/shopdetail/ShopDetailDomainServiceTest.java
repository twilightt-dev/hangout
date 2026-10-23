package com.dianping.shopdetail;

import com.dianping.shop.shopdetail.enums.ReviewTag;
import com.dianping.shop.shopdetail.model.TagEvidence;
import com.dianping.shop.shopdetail.repository.*;
import com.dianping.shop.shopdetail.service.ShopDetailDomainService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 通过领域服务入口验证用户口碑规则，不依赖任务节点实现。
 */
class ShopDetailDomainServiceTest {
    /**
     * 应用用例服务。
     */
    private final ShopDetailDomainService service = new ShopDetailDomainService(mock(ShopDetailRepository.class),
            mock(ShopVoucherRepository.class), mock(ShopSetmealRepository.class),
            mock(ShopReviewRepository.class), mock(RecommendDishRepository.class));

    /**
     * 验证疑问句不会被当成明确正向口碑证据。
     */
    @Test
    void questionsDoNotContributePositiveEvidence() {
        var tags = service.mergeDecisionTags(List.of(), List.of(
                new TagEvidence(1L, List.of(), "好吃吗？停车方便吗？"),
                new TagEvidence(2L, List.of(), "好吃吗?停车方便吗?")));
        assertThat(tags).isEmpty();
    }

    /**
     * 验证跨来源作者去重、标签排序以及否定语料排除。
     */
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
