package com.dianping.domain.comment;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommentLikeModelTest {
    @Test void restoresRealStatusAndRejectsHiddenRepliesAndUnsavedComments() {
        Comment hidden = Comment.restore(1L, 9L, 7L, CommentContent.of("内容"), 0L, 0L, 2);
        assertThat(hidden.status()).isEqualTo(2);
        assertThatThrownBy(hidden::ensureLikeable).isInstanceOf(CommentRuleViolation.class);
        assertThatThrownBy(() -> Comment.restore(2L, 9L, 7L, CommentContent.of("回复"), 1L, 0L, 0).ensureLikeable()).isInstanceOf(CommentRuleViolation.class);
        assertThatThrownBy(() -> Comment.create(9L, 7L, CommentContent.of("新评论")).ensureLikeable()).isInstanceOf(CommentRuleViolation.class);
        assertThatCode(() -> Comment.restore(3L, 9L, 7L, CommentContent.of("旧评论"), 0L, 0L, null).ensureLikeable()).doesNotThrowAnyException();
    }
    @Test void entityEqualityUsesSavedIdentity() {
        Comment a = Comment.restore(1L, 9L, 7L, CommentContent.of("甲"), 0L, 0L, 0);
        Comment b = Comment.restore(1L, 9L, 7L, CommentContent.of("乙"), 0L, 0L, 2);
        assertThat(a).isEqualTo(b);
        assertThat(Comment.create(9L, 7L, CommentContent.of("甲"))).isNotEqualTo(Comment.create(9L, 7L, CommentContent.of("甲")));
    }
    @Test void relationSwitchesInBothDirectionsAndKeepsItsIdentity() {
        CommentLikeId id = new CommentLikeId(1L, 7L);
        assertThat(CommentLike.restore(id, false).toggle()).isEqualTo(new CommentLikeChange(id, false, true));
        assertThat(CommentLike.restore(id, true).toggle()).isEqualTo(new CommentLikeChange(id, true, false));
        assertThatThrownBy(() -> new CommentLikeId(0L, 7L)).isInstanceOf(CommentRuleViolation.class);
    }
}
