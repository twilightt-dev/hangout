package com.dianping.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.VO.CommentVO;
import com.dianping.constant.SystemConstants;
import com.dianping.dto.CreateCommentDTO;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.BlogComments;
import com.dianping.entity.User;
import com.dianping.mapper.BlogMapper;
import com.dianping.mapper.BlogCommentsMapper;
import com.dianping.service.BlogCommentsService;
import com.dianping.service.UserService;
import com.dianping.result.Result;
import com.dianping.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {

    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private UserService userService;

    @Override
    @Transactional
    public Result<Long> createComment(Long blogId, CreateCommentDTO request) {
        if (blogId == null || blogId <= 0) {
            return Result.error("非法博客Id");
        }
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            return Result.error("请先登录");
        }
        if (request == null || request.getContent() == null) {
            return Result.error("评论内容不能为空");
        }
        String content = request.getContent().trim();
        if (content.isEmpty()) {
            return Result.error("评论内容不能为空");
        }
        if (content.length() > 255) {
            return Result.error("评论内容不能超过255个字符");
        }
        if (blogMapper.selectById(blogId) == null) {
            return Result.error("博客不存在");
        }

        BlogComments comment = new BlogComments()
                .setBlogId(blogId)
                .setUserId(currentUser.getId())
                .setParentId(0L)
                .setAnswerId(0L)
                .setContent(content)
                .setStatus(0);
        if (!save(comment)) {
            return Result.error("评论发布失败");
        }

        boolean updated = blogMapper.update(null, new UpdateWrapper<Blog>()
                .eq("id", blogId)//COALESCE是SQL函数，返回第一个不为null的参数，避免null+1还是null
                .setSql("comments = COALESCE(comments, 0) + 1")) > 0;
        if (!updated) {
            throw new IllegalStateException("博客评论数更新失败");
        }
        return Result.success(comment.getId());
    }

    @Override
    public Result<Page<CommentVO>> queryComments(Long blogId, Integer current) {
        if (blogId == null || blogId <= 0) {
            return Result.error("非法博客Id");
        }
        if (current == null || current < 1) {
            return Result.error("页码无效");
        }
        if (blogMapper.selectById(blogId) == null) {
            return Result.error("博客不存在");
        }

        Page<BlogComments> source = new Page<>(current, SystemConstants.MAX_PAGE_SIZE);
        query()
                .eq("blog_id", blogId)
                .eq("parent_id", 0)
                .and(wrapper -> wrapper.eq("status", 0).or().isNull("status"))
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(source);

        List<BlogComments> comments = source.getRecords();
        List<Long> userIds = comments.stream()
                .map(BlogComments::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> users = userIds.isEmpty()
                ? Collections.emptyMap()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));

        List<CommentVO> records = comments.stream().map(comment -> {
            CommentVO vo = new CommentVO();
            vo.setId(comment.getId());
            vo.setBlogId(comment.getBlogId());
            vo.setUserId(comment.getUserId());
            vo.setContent(comment.getContent());
            vo.setCreateTime(comment.getCreateTime());
            vo.setUpdateTime(comment.getUpdateTime());
            User user = users.get(comment.getUserId());
            if (user != null) {
                vo.setUserName(user.getNickName());
                vo.setUserIcon(user.getIcon());
            }
            return vo;
        }).toList();

        Page<CommentVO> resultPage = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        resultPage.setRecords(records);
        return Result.success(resultPage);
    }

}
