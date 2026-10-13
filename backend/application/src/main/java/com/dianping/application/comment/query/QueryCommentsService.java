package com.dianping.application.comment.query;

import com.dianping.application.comment.port.BlogCommentTarget;
import com.dianping.application.comment.port.CommentAuthorDirectory;
import com.dianping.application.comment.port.CommentReadPort;
import com.dianping.application.comment.port.CurrentCommentAuthor;
import com.dianping.application.comment.port.CommentLikeReadPort;
import com.dianping.application.comment.port.CommentLikeStatus;
import com.dianping.application.comment.like.CommentLikeUnavailableException;
import com.dianping.domain.comment.CommentRuleViolation;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class QueryCommentsService {
    private final BlogCommentTarget blogs;
    private final CommentReadPort comments;
    private final CommentAuthorDirectory authors;
    private final CurrentCommentAuthor identity;
    private final CommentLikeReadPort likes;

    public QueryCommentsService(BlogCommentTarget blogs, CommentReadPort comments,
                                CommentAuthorDirectory authors, CurrentCommentAuthor identity,
                                CommentLikeReadPort likes) {
        this.blogs = blogs;
        this.comments = comments;
        this.authors = authors;
        this.identity = identity;
        this.likes = likes;
    }

    public CommentPage<CommentView> query(Long blogId, Integer current) {
        if (blogId == null || blogId <= 0) {
            throw new CommentRuleViolation("非法博客Id");
        }
        if (current == null || current < 1) {
            throw new CommentRuleViolation("页码无效");
        }
        if (!blogs.exists(blogId)) {
            throw new CommentRuleViolation("博客不存在");
        }
        CommentPage<CommentRow> source = comments.query(blogId, current);
        List<Long> authorIds = source.records().stream().map(CommentRow::userId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CommentAuthor> authorMap = authorIds.isEmpty()
                ? Map.of() : authors.findAuthors(authorIds);
        Long userId = identity.currentUserId();
        Map<Long, CommentLikeStatus> states;
        try { states = source.records().isEmpty() ? Map.of()
                : likes.findStates(source.records().stream().map(CommentRow::id).toList(), userId); }
        catch (CommentLikeUnavailableException failure) { states = Map.of(); }
        Map<Long, CommentLikeStatus> stateMap = states;
        List<CommentView> records = source.records().stream().map(row -> {
            CommentAuthor author = row.userId() == null ? null : authorMap.get(row.userId());
            CommentLikeStatus like = stateMap.getOrDefault(row.id(), CommentLikeStatus.unavailable(userId));
            return new CommentView(row.id(), row.blogId(), row.userId(),
                    author == null ? null : author.userName(), author == null ? null : author.userIcon(),
                    row.content(), row.createTime(), row.updateTime(), row.liked(), like.isLike(), like.available());
        }).toList();
        return new CommentPage<>(source.current(), source.size(), source.total(), records);
    }
}
