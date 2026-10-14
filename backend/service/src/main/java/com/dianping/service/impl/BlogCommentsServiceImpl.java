package com.dianping.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.VO.CommentVO;
import com.dianping.constant.SystemConstants;
import com.dianping.constant.RedisConstants;
import com.dianping.dto.CreateCommentDTO;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.BlogComments;
import com.dianping.entity.User;
import com.dianping.mapper.BlogMapper;
import com.dianping.mapper.BlogCommentsMapper;
import com.dianping.service.BlogCommentsService;
import com.dianping.service.UserService;
import com.dianping.service.BlogCommentsService.LikeResult;
import com.dianping.result.Result;
import com.dianping.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {

    @Autowired
    private BlogMapper blogMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

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
                .setStatus(0)
                .setLiked(0L);
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

        UserDTO currentUser = UserHolder.getUser();
        Long userId = currentUser == null ? null : currentUser.getId();

        List<CommentVO> records = comments.stream().map(comment -> {
            CommentVO vo = new CommentVO();
            vo.setId(comment.getId());
            vo.setBlogId(comment.getBlogId());
            vo.setUserId(comment.getUserId());
            vo.setContent(comment.getContent());
            vo.setCreateTime(comment.getCreateTime());
            vo.setUpdateTime(comment.getUpdateTime());
            vo.setLiked(comment.getLiked() == null ? 0 : comment.getLiked());
            User user = users.get(comment.getUserId());
            if (user != null) {
                vo.setUserName(user.getNickName());
                vo.setUserIcon(user.getIcon());
            }
            return vo;
        }).toList();
        fillLikeStates(records, userId);

        Page<CommentVO> resultPage = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        resultPage.setRecords(records);
        return Result.success(resultPage);
    }

    @Override
    public Result<LikeResult> toggleCommentLike(Long blogId, Long commentId) {
        if (blogId == null || blogId <= 0) return Result.error("非法博客Id");
        if (commentId == null || commentId <= 0) return Result.error("非法评论Id");
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null || user.getId() <= 0) return Result.error("请先登录");

        RLock lock = null;
        boolean acquired = false;
        try {
            lock = redissonClient.getLock(RedisConstants.COMMENT_LIKED_LOCK_KEY + commentId + ":" + user.getId());
            acquired = lock.tryLock(1, TimeUnit.SECONDS);
            if (!acquired) return Result.error("操作频繁，请稍后重试");
            BlogComments comment = getById(commentId);
            if (comment == null) return Result.error("评论不存在");
            if (!blogId.equals(comment.getBlogId())) return Result.error("评论不属于该博客");
            if (!Long.valueOf(0).equals(comment.getParentId())
                    || (comment.getStatus() != null && comment.getStatus() != 0)) {
                return Result.error("评论不允许点赞");
            }

            String key = likeKey(commentId);
            Long relations = stringRedisTemplate.opsForSet().size(key);
            if (relations == null) throw new IllegalStateException("未取得点赞关系数量");
            if (comment.getLiked() != null && comment.getLiked() > 0 && relations == 0) {
                return Result.error("评论点赞数据待核对");
            }
            Boolean originalLiked = stringRedisTemplate.opsForSet().isMember(key, user.getId().toString());
            if (originalLiked == null) throw new IllegalStateException("未取得点赞状态");

            // 先更新数据库，再修改 Set。两者部分成功时返回错误，不补偿或重放切换。
            int updated = originalLiked ? baseMapper.decrementLiked(commentId, blogId)
                    : baseMapper.incrementLiked(commentId, blogId);
            if (updated != 1) return Result.error("点赞操作失败，请刷新后重试");
            Long changed = originalLiked ? stringRedisTemplate.opsForSet().remove(key, user.getId().toString())
                    : stringRedisTemplate.opsForSet().add(key, user.getId().toString());
            if (!Long.valueOf(1).equals(changed)) throw new IllegalStateException("点赞关系更新结果异常");
            BlogComments current = getById(commentId);
            if (current == null || current.getLiked() == null) throw new IllegalStateException("未取得更新后的点赞数");
            return Result.success(new LikeResult(current.getLiked(), !originalLiked));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "点赞服务暂时不可用", interrupted);
        } catch (RuntimeException failure) {
            log.warn("评论点赞失败，评论={}，用户={}；部分写入可能需要核对", commentId, user.getId(), failure);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "点赞服务暂时不可用", failure);
        } finally {
            if (acquired) {
                try { lock.unlock(); }
                catch (RuntimeException failure) { log.warn("释放评论点赞锁失败，评论={}", commentId, failure); }
            }
        }
    }

    /** 仅为兼容旧关系 Set 保留分区键名，不再使用同步 Hash 或队列。 */
    private static String likeKey(Long commentId) {
        return "{" + RedisConstants.COMMENT_LIKE_KEY + ":" + Math.floorMod(commentId, 4) + "}:" + commentId + ":users";
    }

    /** 一页评论的关系读取使用管道；故障时保留数据库数量，禁用点赞按钮。 */
    private void fillLikeStates(List<CommentVO> records, Long userId) {
        records.forEach(vo -> {
            vo.setIsLike(userId == null ? false : null);
            vo.setLikeAvailable(false);
        });
        if (records.isEmpty()) return;
        try {
            List<Object> states = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (CommentVO vo : records) {
                    byte[] key = likeKey(vo.getId()).getBytes(StandardCharsets.UTF_8);
                    connection.setCommands().sCard(key);
                    if (userId != null) connection.setCommands().sIsMember(key, userId.toString().getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            int stride = userId == null ? 1 : 2;
            if (states.size() != records.size() * stride) throw new IllegalStateException("点赞状态返回数量异常");
            for (int i = 0; i < records.size(); i++) {
                CommentVO vo = records.get(i);
                long count = (Long) states.get(i * stride);
                boolean available = !(vo.getLiked() > 0 && count == 0);
                vo.setLikeAvailable(available);
                vo.setIsLike(userId == null ? Boolean.FALSE : available ? (Boolean) states.get(i * stride + 1) : null);
            }
        } catch (RuntimeException failure) {
            records.forEach(vo -> {
                vo.setIsLike(userId == null ? false : null);
                vo.setLikeAvailable(false);
            });
            log.warn("评论点赞状态读取失败，保留评论列表和数据库数量", failure);
        }
    }
}
