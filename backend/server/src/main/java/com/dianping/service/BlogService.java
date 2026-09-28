package com.dianping.service;

import com.dianping.VO.BlogVO;
import com.dianping.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.result.Result;

import java.util.List;


public interface BlogService extends IService<Blog> {

    Result<BlogVO> queryBlogById(Long blogId);

    Result<Void> likeBlog(Long id);

    Result<List<Blog>> pageQueryHotBlog(Integer current);
}
