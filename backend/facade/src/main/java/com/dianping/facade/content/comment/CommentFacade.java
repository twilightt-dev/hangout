package com.dianping.facade.content.comment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.VO.CommentVO;
import com.dianping.application.comment.create.CreateCommentService;
import com.dianping.application.comment.query.QueryCommentsService;
import com.dianping.application.comment.create.CreateCommentCommand;
import com.dianping.application.comment.query.CommentPage;
import com.dianping.application.comment.query.CommentView;
import com.dianping.domain.comment.CommentRuleViolation;
import com.dianping.application.comment.like.ToggleCommentLikeService;
import com.dianping.application.comment.like.CommentLikeResult;
import com.dianping.dto.CreateCommentDTO;
import com.dianping.result.Result;
import org.springframework.stereotype.Component;

@Component
public class CommentFacade {
    private final CreateCommentService create;
    private final QueryCommentsService query;
    private final ToggleCommentLikeService likes;

    public CommentFacade(CreateCommentService create, QueryCommentsService query, ToggleCommentLikeService likes) {
        this.create = create;
        this.query = query;
        this.likes = likes;
    }

    public Result<CommentLikeResult> toggleCommentLike(Long blogId, Long commentId) {
        try { return Result.success(likes.toggle(blogId, commentId)); }
        catch (CommentRuleViolation rejected) { return Result.error(rejected.getMessage()); }
    }

    public Result<Long> createComment(Long blogId, CreateCommentDTO request) {
        try {
            return Result.success(create.create(new CreateCommentCommand(blogId,
                    request == null ? null : request.getContent())));
        } catch (CommentRuleViolation rejected) {
            return Result.error(rejected.getMessage());
        }
    }

    public Result<Page<CommentVO>> queryComments(Long blogId, Integer current) {
        try {
            CommentPage<CommentView> source = query.query(blogId, current);
            // 对外继续使用原 Page，确保分页附加字段和默认值保持兼容。
            Page<CommentVO> result = new Page<>(source.current(), source.size(), source.total());
            result.setRecords(source.records().stream().map(this::toVO).toList());
            return Result.success(result);
        } catch (CommentRuleViolation rejected) {
            return Result.error(rejected.getMessage());
        }
    }

    private CommentVO toVO(CommentView source) {
        CommentVO result = new CommentVO();
        result.setId(source.id());
        result.setBlogId(source.blogId());
        result.setUserId(source.userId());
        result.setUserName(source.userName());
        result.setUserIcon(source.userIcon());
        result.setContent(source.content());
        result.setCreateTime(source.createTime());
        result.setUpdateTime(source.updateTime());
        result.setLiked(source.liked());
        result.setIsLike(source.isLike());
        result.setLikeAvailable(source.likeAvailable());
        return result;
    }
}
