package com.dianping.application.comment;

import com.dianping.application.comment.create.CreateCommentCommand;
import com.dianping.application.comment.create.CreateCommentService;
import com.dianping.application.comment.port.BlogCommentTarget;
import com.dianping.application.comment.port.CommentAuthorDirectory;
import com.dianping.application.comment.port.CommentReadPort;
import com.dianping.application.comment.port.CurrentCommentAuthor;
import com.dianping.application.comment.query.CommentAuthor;
import com.dianping.application.comment.query.CommentPage;
import com.dianping.application.comment.query.CommentRow;
import com.dianping.application.comment.query.CommentView;
import com.dianping.application.comment.query.QueryCommentsService;
import com.dianping.domain.comment.Comment;
import com.dianping.domain.comment.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CommentApplicationServicesTest {
    private final MemoryPorts ports = new MemoryPorts();
    private CreateCommentService create;
    private QueryCommentsService query;

    @BeforeEach
    void setUp() {
        create = new CreateCommentService(ports, ports, ports);
        query = new QueryCommentsService(ports, ports, ports, () -> null, (ids, user) -> Map.of());
    }

    // 防止改变业务验证顺序或在无效请求中执行数据库写入。
    @Test
    void invalidBlogPrecedesIdentityAndContent() {
        ports.userId = null;
        assertThatThrownBy(() -> create.create(new CreateCommentCommand(0L, null)))
                .hasMessage("非法博客Id");
        assertThat(ports.events).isEmpty();
    }

    @Test
    void identityPrecedesContentAndBlogLookup() {
        ports.userId = null;
        assertThatThrownBy(() -> create.create(new CreateCommentCommand(9L, null)))
                .hasMessage("请先登录");
        assertThat(ports.events).containsExactly("identity");
    }

    @Test
    void invalidContentPrecedesBlogLookup() {
        ports.exists = false;
        for (String content : Arrays.asList(null, "\t ", "a".repeat(256))) {
            assertThatThrownBy(() -> create.create(new CreateCommentCommand(9L, content)))
                    .hasMessage(content != null && content.length() == 256
                            ? "评论内容不能超过255个字符" : "评论内容不能为空");
        }
        assertThat(ports.events).containsExactly("identity", "identity", "identity");
    }

    @Test
    void missingBlogCannotBeSaved() {
        ports.exists = false;
        assertThatThrownBy(() -> create.create(new CreateCommentCommand(9L, "评论")))
                .hasMessage("博客不存在");
        assertThat(ports.saved).isNull();
        assertThat(ports.increments).isZero();
    }

    @Test
    void successfulCreationUsesServerIdentityAndIncrementsTheSameBlog() {
        assertThat(create.create(new CreateCommentCommand(9L, "  很好  "))).isEqualTo(21L);
        assertThat(ports.saved.authorId()).isEqualTo(7L);
        assertThat(ports.saved.blogId()).isEqualTo(9L);
        assertThat(ports.saved.content().value()).isEqualTo("很好");
        assertThat(ports.incrementedBlog).isEqualTo(9L);
        assertThat(ports.increments).isEqualTo(1);
        assertThat(ports.events).containsExactly("identity", "exists", "insert", "increment");
    }

    @Test
    void failedSaveDoesNotIncrementBlog() {
        ports.savedId = null;
        assertThatThrownBy(() -> create.create(new CreateCommentCommand(9L, "评论")))
                .hasMessage("评论发布失败");
        assertThat(ports.increments).isZero();
    }

    @Test
    void zeroRowCountUpdateRaisesRollbackException() {
        ports.incrementResult = false;
        assertThatThrownBy(() -> create.create(new CreateCommentCommand(9L, "评论")))
                .isInstanceOf(IllegalStateException.class).hasMessage("博客评论数更新失败");
        assertThat(ports.saved).isNotNull();
    }

    @Test
    void queryValidatesBlogThenPageThenExistence() {
        assertThatThrownBy(() -> query.query(0L, 0)).hasMessage("非法博客Id");
        assertThatThrownBy(() -> query.query(9L, null)).hasMessage("页码无效");
        assertThatThrownBy(() -> query.query(9L, 0)).hasMessage("页码无效");
        assertThat(ports.events).isEmpty();
        ports.exists = false;
        assertThatThrownBy(() -> query.query(9L, 1)).hasMessage("博客不存在");
        assertThat(ports.events).containsExactly("exists");
    }

    @Test
    void queryEnrichesOneBatchOfDistinctAuthorsWithoutDroppingMissingOnes() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 12, 10, 0);
        ports.rows = new CommentPage<>(2, 10, 23, List.of(
                new CommentRow(21L, 9L, 7L, "甲", created, created),
                new CommentRow(20L, 9L, 7L, "乙", created, created),
                new CommentRow(19L, 9L, 8L, "丙", created, created),
                new CommentRow(18L, 9L, null, "丁", created, created)));
        CommentPage<CommentView> page = query.query(9L, 2);
        assertThat(page).isNotNull();
        assertThat(page.current()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.total()).isEqualTo(23);
        assertThat(page.records()).extracting(CommentView::id).containsExactly(21L, 20L, 19L, 18L);
        assertThat(page.records().get(0).userName()).isEqualTo("小林");
        assertThat(page.records().get(0).userIcon()).isEqualTo("/avatar.png");
        assertThat(page.records().get(2).userName()).isNull();
        assertThat(page.records().get(3).userName()).isNull();
        assertThat(ports.authorBatches).containsExactly(List.of(7L, 8L));
        assertThat(ports.queriedBlog).isEqualTo(9L);
        assertThat(ports.queriedPage).isEqualTo(2);
    }

    @Test
    void emptyPageDoesNotLoadAuthors() {
        CommentPage<CommentView> page = query.query(9L, 1);
        assertThat(page).isNotNull();
        assertThat(page.records()).isEmpty();
        assertThat(ports.authorBatches).isEmpty();
    }

    private static class MemoryPorts implements CommentRepository, CurrentCommentAuthor,
            BlogCommentTarget, CommentReadPort, CommentAuthorDirectory {
        Long userId = 7L;
        boolean exists = true;
        Long savedId = 21L;
        boolean incrementResult = true;
        Comment saved;
        int increments;
        Long incrementedBlog;
        Long queriedBlog;
        int queriedPage;
        final List<String> events = new ArrayList<>();
        final List<List<Long>> authorBatches = new ArrayList<>();
        CommentPage<CommentRow> rows = new CommentPage<>(1, 10, 0, List.of());

        public Long currentUserId() { events.add("identity"); return userId; }
        public boolean exists(Long blogId) { events.add("exists"); return exists; }
        public Long insert(Comment comment) { events.add("insert"); saved = comment; return savedId; }
        public Comment findById(Long id) { return null; }
        public boolean incrementCount(Long blogId) {
            events.add("increment"); increments++; incrementedBlog = blogId; return incrementResult;
        }
        public CommentPage<CommentRow> query(Long blogId, int current) {
            queriedBlog = blogId; queriedPage = current; return rows;
        }
        public Map<Long, CommentAuthor> findAuthors(List<Long> ids) {
            authorBatches.add(List.copyOf(ids));
            return Map.of(7L, new CommentAuthor("小林", "/avatar.png"));
        }
    }
}
