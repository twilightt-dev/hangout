package com.dianping.application.comment.like;

import com.dianping.application.comment.port.*;
import com.dianping.domain.comment.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ToggleCommentLikeService {
    private final CurrentCommentAuthor identity;
    private final CommentRepository comments;
    private final CommentLikeEligibilityPort eligibility;
    private final CommentLikeRepository likes;
    @Value("${comment-like.write.enabled:true}")
    private boolean writesEnabled = true;
    public ToggleCommentLikeService(CurrentCommentAuthor identity, CommentRepository comments,
                                    CommentLikeEligibilityPort eligibility, CommentLikeRepository likes) {
        this.identity = identity; this.comments = comments; this.eligibility = eligibility; this.likes = likes;
    }
    public CommentLikeResult toggle(Long blogId, Long commentId) {
        if (!writesEnabled) throw new CommentLikeUnavailableException();
        if (blogId == null || blogId <= 0) throw new CommentRuleViolation("非法博客Id");
        CommentLikeId id = new CommentLikeId(commentId, identity.currentUserId());
        Comment comment = comments.findById(commentId);
        if (comment == null) throw new CommentRuleViolation("评论不存在");
        if (!comment.blogId().equals(blogId)) throw new CommentRuleViolation("评论不属于该博客");
        comment.ensureLikeable();
        CommentLikeMetadata metadata = eligibility.metadata(commentId);
        if (metadata == null || !metadata.enabled()) throw new CommentRuleViolation("评论点赞数据待核对");
        // 仅在仓储明确保证没有执行的竞争中重新计算；技术异常直接传播。
        for (int attempt = 0; attempt < 3; attempt++) {
            CommentLike relation = likes.findState(id, metadata.epoch(), metadata.canInitialize());
            CommentLikeApplyResult applied = likes.tryApply(relation.toggle(), metadata.epoch());
            if (applied.applied()) return new CommentLikeResult(applied.liked(), applied.isLike());
        }
        throw new CommentRuleViolation("点赞操作冲突，请刷新状态后重试");
    }
}
