package com.dianping.infrastructure.persistence.adapter.comment;

import com.dianping.application.comment.port.BlogCommentTarget;
import com.dianping.mapper.BlogMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.dianping.entity.Blog;
import org.springframework.stereotype.Repository;

@Repository
public class BlogCommentTargetAdapter implements BlogCommentTarget {
    private final BlogMapper mapper;

    public BlogCommentTargetAdapter(BlogMapper mapper) { this.mapper = mapper; }

    public boolean exists(Long blogId) { return mapper.selectById(blogId) != null; }

    public boolean incrementCount(Long blogId) {
        return mapper.update(null, new UpdateWrapper<Blog>().eq("id", blogId)
                .setSql("comments = COALESCE(comments, 0) + 1")) > 0;
    }
}
