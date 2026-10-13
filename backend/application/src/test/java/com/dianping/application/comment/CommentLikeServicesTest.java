package com.dianping.application.comment;

import com.dianping.application.comment.like.*;
import com.dianping.application.comment.like.sync.*;
import com.dianping.application.comment.port.*;
import com.dianping.domain.comment.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CommentLikeServicesTest {
    final MemoryLikes memory = new MemoryLikes();
    final CommentRepository comments = new CommentRepository() {
        public Long insert(Comment comment) { return 1L; }
        public Comment findById(Long id) { return id == 404 ? null : Comment.restore(id, 9L, 7L, CommentContent.of("内容"), 0L, 0L, 0); }
    };
    ToggleCommentLikeService service() { return new ToggleCommentLikeService(() -> 7L, comments, id -> new CommentLikeMetadata(memory.epoch, 0, 0), memory); }
    @Test void selfLikeAndSecondRequestCancel() {
        assertThat(service().toggle(9L, 1L)).isEqualTo(new CommentLikeResult(1, true));
        assertThat(service().toggle(9L, 1L)).isEqualTo(new CommentLikeResult(0, false));
    }
    @Test void rejectsWrongPathMissingCommentAndUnverifiedHistoryBeforeAnyWrite() {
        assertThatThrownBy(() -> service().toggle(10L, 1L)).isInstanceOf(CommentRuleViolation.class);
        assertThatThrownBy(() -> service().toggle(9L, 404L)).isInstanceOf(CommentRuleViolation.class);
        memory.epoch = 0;
        assertThatThrownBy(() -> service().toggle(9L, 1L)).hasMessage("评论点赞数据待核对");
        assertThat(memory.writes).isZero();
    }
    @Test void knownCompetitionMayRetryButUnknownResultNeverRetries() {
        memory.conflicts = 1;
        assertThat(service().toggle(9L, 1L).isLike()).isTrue();
        assertThat(memory.writes).isEqualTo(2);
        memory.writes = 0; memory.fail = true;
        assertThatThrownBy(() -> service().toggle(9L, 1L)).isInstanceOf(CommentLikeUnavailableException.class);
        assertThat(memory.writes).isEqualTo(1);
    }
    @Test void failedSnapshotIsDeferredAndDoesNotBlockOtherComments() {
        var first = new CommentLikeCountSnapshot(1L, 1, 1, 1);
        var second = new CommentLikeCountSnapshot(2L, 1, 2, 2);
        List<Long> acked = new ArrayList<>(); List<Long> deferred = new ArrayList<>();
        var port = new CommentLikeCountSyncPort() {
            public List<CommentLikeCountSnapshot> pending(int limit) { return List.of(first, second); }
            public boolean writeCount(CommentLikeCountSnapshot snapshot) { if (snapshot.commentId() == 1) throw new IllegalStateException("数据库故障"); return true; }
            public void acknowledge(CommentLikeCountSnapshot snapshot) { acked.add(snapshot.commentId()); }
            public void defer(CommentLikeCountSnapshot snapshot) { deferred.add(snapshot.commentId()); }
        };
        new SyncCommentLikeCountsService(port).synchronize(100);
        assertThat(acked).containsExactly(2L); assertThat(deferred).containsExactly(1L);
    }
    static class MemoryLikes implements CommentLikeRepository {
        boolean liked; boolean fail; int conflicts; int writes; long epoch = 1;
        public CommentLike findState(CommentLikeId id, long epoch, boolean initialize) { return CommentLike.restore(id, liked); }
        public CommentLikeApplyResult tryApply(CommentLikeChange change, long epoch) {
            writes++;
            if (fail) throw new CommentLikeUnavailableException();
            if (conflicts-- > 0) return new CommentLikeApplyResult(false, liked, liked ? 1 : 0);
            liked = change.targetLiked(); return new CommentLikeApplyResult(true, liked, liked ? 1 : 0);
        }
    }
}
