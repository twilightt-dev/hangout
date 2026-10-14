package com.dianping.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.VO.CommentVO;
import com.dianping.dto.CreateCommentDTO;
import com.dianping.result.Result;
import com.dianping.service.BlogCommentsService;
import com.dianping.service.BlogCommentsService.LikeResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blog")
@Tag(name = "博客评论接口")
public class BlogCommentsController {

    private final BlogCommentsService commentService;

    public BlogCommentsController(BlogCommentsService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/{blogId}/comments")
    @Operation(summary = "发表评论")
    public Result<Long> createComment(@PathVariable Long blogId,
                                      @Valid @RequestBody CreateCommentDTO request) {
        return commentService.createComment(blogId, request);
    }

    @PostMapping("/{blogId}/comments/{commentId}/like")
    @Operation(summary = "切换评论点赞状态")
    public Result<LikeResult> toggleCommentLike(@PathVariable Long blogId, @PathVariable Long commentId) {
        return commentService.toggleCommentLike(blogId, commentId);
    }

    @GetMapping("/{blogId}/comments")
    @Operation(summary = "分页查询博客评论")
    public Result<Page<CommentVO>> queryComments(
            @PathVariable Long blogId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return commentService.queryComments(blogId, current);
    }

}
