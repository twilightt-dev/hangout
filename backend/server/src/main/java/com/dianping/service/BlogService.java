package com.dianping.service;

import com.dianping.VO.BlogVO;
import com.dianping.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.result.Result;

import java.util.List;


public interface BlogService extends IService<Blog> {

    Result<BlogVO> queryBlogById(Long blogId);

    Result<Void> likeBlog(Long id);

    Result<List<Blog>> pageQueryHotBlog(Integer current);

    Result<Page<BlogVO>> queryBlogsByUser(Long userId, Integer current);
}
