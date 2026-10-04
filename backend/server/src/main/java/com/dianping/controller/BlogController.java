package com.dianping.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.VO.BlogVO;
import com.dianping.constant.SystemConstants;
import com.dianping.dto.UserDTO;
import com.dianping.utils.UserHolder;
import com.dianping.result.Result;
import com.dianping.entity.Blog;
import com.dianping.entity.User;
import com.dianping.service.BlogService;
import com.dianping.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
@Tag(name = "博客接口")
public class BlogController {
    @Autowired
    private BlogService blogService;
    @Autowired
    private UserService userService;

    @PostMapping("/post")
    @Operation(summary = "发布探店博客")
    public Result<Long> saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.success(blog.getId());
    }

    @GetMapping("/view/{id}")
    @Operation(summary = "查询博客详情")
    public Result<BlogVO> viewBlog(@PathVariable("id") Long blogId){
        return blogService.queryBlogById(blogId) ;

    }


    @PutMapping("/like/{id}")
    @Operation(summary = "点赞博客")
    public Result<Void> likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id) ;
    }

    @GetMapping("/of/me")
    @Operation(summary = "查询当前用户的博客")
    public Result<List<Blog>> queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.success(records);
    }

    @GetMapping("/of/user")
    @Operation(summary = "根据id分页查询用户的博客")
    public Result<Page<BlogVO>> queryUserBlogs(
            @RequestParam("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryBlogsByUser(userId, current);
    }

    @GetMapping("/hot")
    @Operation(summary = "分页查询热门博客")
    public Result<List<Blog>> pageQueryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.pageQueryHotBlog( current);
    }
}
