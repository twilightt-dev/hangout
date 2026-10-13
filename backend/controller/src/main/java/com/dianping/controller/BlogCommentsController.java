package com.dianping.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.VO.CommentVO;
import com.dianping.dto.CreateCommentDTO;
import com.dianping.result.Result;
import com.dianping.facade.content.comment.CommentFacade;
import com.dianping.application.comment.like.CommentLikeResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blog")
@Tag(name = "博客评论接口")
public class BlogCommentsController {

    private final CommentFacade commentFacade;

    public BlogCommentsController(CommentFacade commentFacade) {
        this.commentFacade = commentFacade;
    }

    @PostMapping("/{blogId}/comments")
    @Operation(summary = "发表评论")
    public Result<Long> createComment(@PathVariable Long blogId,
                                      @Valid @RequestBody CreateCommentDTO request) {
        return commentFacade.createComment(blogId, request);
    }

    ///current表示当前要查询的页码
    @PostMapping("/{blogId}/comments/{commentId}/like")
    @Operation(summary = "切换评论点赞状态")
    public Result<CommentLikeResult> toggleCommentLike(@PathVariable Long blogId, @PathVariable Long commentId) {
        return commentFacade.toggleCommentLike(blogId, commentId);
    }

    @GetMapping("/{blogId}/comments")
    @Operation(summary = "分页查询博客评论")
    public Result<Page<CommentVO>> queryComments(
            @PathVariable Long blogId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return commentFacade.queryComments(blogId, current);
    }

}
