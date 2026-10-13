package com.dianping.application.comment.create;

import com.dianping.application.comment.port.BlogCommentTarget;
import com.dianping.application.comment.port.CurrentCommentAuthor;
import com.dianping.domain.comment.CommentRepository;
import com.dianping.domain.comment.Comment;
import com.dianping.domain.comment.CommentContent;
import com.dianping.domain.comment.CommentRuleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCommentService {
    private final CommentRepository repository;
    private final CurrentCommentAuthor identity;
    private final BlogCommentTarget blogs;

    public CreateCommentService(CommentRepository repository, CurrentCommentAuthor identity,
                                BlogCommentTarget blogs) {
        this.repository = repository;
        this.identity = identity;
        this.blogs = blogs;
    }

    @Transactional
    public Long create(CreateCommentCommand command) {
        Long blogId = command.blogId();
        if (blogId == null || blogId <= 0) {
            throw new CommentRuleViolation("非法博客Id");
        }
        Long authorId = identity.currentUserId();
        if (authorId == null) {
            throw new CommentRuleViolation("请先登录");
        }
        CommentContent content = CommentContent.of(command.content());
        if (!blogs.exists(blogId)) {
            throw new CommentRuleViolation("博客不存在");
        }
        Long commentId = repository.insert(Comment.create(blogId, authorId, content));
        if (commentId == null) {
            throw new CommentRuleViolation("评论发布失败");
        }
        if (!blogs.incrementCount(blogId)) {
            // 技术失败不得转换成正常返回，否则已插入的评论不会回滚。
            throw new IllegalStateException("博客评论数更新失败");
        }
        return commentId;
    }
}
