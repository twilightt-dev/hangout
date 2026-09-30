package com.dianping.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.VO.CommentVO;
import com.dianping.dto.CreateCommentDTO;
import com.dianping.result.Result;
import com.dianping.service.BlogCommentsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blog")
@Tag(name = "博客评论接口")
public class BlogCommentsController {

    @Autowired
    private BlogCommentsService blogCommentsService;

    @PostMapping("/{blogId}/comments")
    @Operation(summary = "发表评论")
    public Result<Long> createComment(@PathVariable Long blogId,
                                      @Valid @RequestBody CreateCommentDTO request) {
        return blogCommentsService.createComment(blogId, request);
    }

    ///current表示当前要查询的页码
    @GetMapping("/{blogId}/comments")
    @Operation(summary = "分页查询博客评论")
    public Result<Page<CommentVO>> queryComments(
            @PathVariable Long blogId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryComments(blogId, current);
    }

}
