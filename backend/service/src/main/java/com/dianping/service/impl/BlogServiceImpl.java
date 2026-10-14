package com.dianping.service.impl;

import com.dianping.VO.BlogVO;
import com.dianping.constant.RedisConstants;
import com.dianping.constant.SystemConstants;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.User;
import com.dianping.feed.FeedTaskRepository;
import com.dianping.mapper.BlogMapper;
import com.dianping.result.Result;
import com.dianping.service.BlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.service.UserService;
import com.dianping.utils.UserHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {
    @Autowired
    private UserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private FeedTaskRepository feedTasks;

    @Override
    @Transactional
    public Result<Long> publishBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) return Result.error("请先登录");
        // 发布 ID、作者、时间和计数均由服务端维护，不能由请求伪造。
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        blog.setId(null).setUserId(user.getId()).setCreateTime(now).setUpdateTime(now).setLiked(0).setComments(0);
        if (!save(blog)) throw new IllegalStateException("博客发布失败");
        feedTasks.publish(blog.getId(), user.getId());
        return Result.success(blog.getId());
    }

    @Override
    public Result<BlogVO> queryBlogById(Long blogId) {
        Blog blog = getById(blogId);
        if(blog == null)return Result.error("博客不存在!") ;

        BlogVO blogVO = new BlogVO();
        BeanUtils.copyProperties(blog,blogVO);
        //博客对应的用户
        User user = userService.getById(blog.getUserId());
        if(user == null)return Result.error("用户不存在!");

        //当前用户
        UserDTO currentUser = UserHolder.getUser() ;
        Boolean isLiked = false ;//匿名用户默认是false没点赞就行

        if(currentUser != null){
            isLiked = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKE_KEY + blogId ,
                    currentUser.getId().toString());
        }

        if(isLiked == null)return Result.error("点赞状态获取失败") ;

        blogVO.setIsLike(isLiked);
        blogVO.setName(user.getNickName());
        blogVO.setIcon(user.getIcon());

        return Result.success(blogVO) ;
    }

    @Override
    public Result<Void> likeBlog(Long blogId) {
        if(blogId == null || blogId <= 0)return Result.error("非法博客Id") ;

        Long userId = UserHolder.getUser().getId();
        String likeKey = RedisConstants.BLOG_LIKE_KEY + blogId ;
        RLock lock = redissonClient.getLock(RedisConstants.BLOG_LIKED_LOCK_KEY + blogId
                + ":" + userId);

        try{
            //没抢到锁最多重试1s返回
            if(!lock.tryLock(1, TimeUnit.SECONDS)){
                return Result.error("操作频繁，请稍后再试") ;
            }

            try{
                if(getById(blogId) == null)return Result.error("博客不存在!") ;
                Boolean isLiked = stringRedisTemplate.opsForSet().isMember(likeKey , userId.toString()) ;
                if(isLiked == null){
                    return Result.error("点赞状态查询失败") ;
                }
                boolean updated = isLiked ? update().setSql("liked = liked - 1")
                                                                 .eq("id" , blogId).gt("liked" , 0)
                                                                 .update() :
                        update().setSql("liked = liked + 1").eq("id" , blogId).update() ;

                if(!updated)return Result.error("点赞操作失败") ;

                if(isLiked){
                    stringRedisTemplate.opsForSet().remove(likeKey , userId.toString()) ;
                }else{
                    stringRedisTemplate.opsForSet().add(likeKey , userId.toString()) ;
                }
                return Result.success() ;
            }finally{
                lock.unlock();
            }

        } catch (RuntimeException e) {
            log.error("博客点赞失败,blogId ={} , userId ={}" , blogId , userId ,e) ;
            return Result.error("点赞服务暂时不可用") ;
        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.error("点赞操作异常中断") ;
        }
    }


    @Override
    public Result<List<Blog>> pageQueryHotBlog(Integer page) {
        if (page == null || page < 1) {
            return Result.error("页码无效");
        }

        List<Blog> blogs = query()
                .orderByDesc("liked")
                .orderByDesc("id")
                .page(new Page<>(page, SystemConstants.MAX_PAGE_SIZE))
                .getRecords();
        UserDTO currentUser = UserHolder.getUser();
        String currentUserId = currentUser == null || currentUser.getId() == null
                ? null : currentUser.getId().toString();

        for (Blog blog : blogs) {
            User author = userService.getById(blog.getUserId());
            if (author != null) {
                blog.setName(author.getNickName());
                blog.setIcon(author.getIcon());
            }

            if (currentUserId == null) {
                blog.setIsLike(false);
                continue;
            }
            Boolean isLiked = stringRedisTemplate.opsForSet().isMember(
                    RedisConstants.BLOG_LIKE_KEY + blog.getId(), currentUserId);
            if (isLiked == null) {
                return Result.error("点赞状态获取失败");
            }
            blog.setIsLike(isLiked);
        }
        return Result.success(blogs);
    }

    @Override
    public Result<Page<BlogVO>> queryBlogsByUser(Long userId, Integer current) {
        if (userId == null || userId <= 0) {
            return Result.error("用户ID非法");
        }
        if (current == null || current < 1) {
            return Result.error("页码无效");
        }

        User author = userService.getById(userId);
        if (author == null) {
            return Result.error("用户不存在");
        }

        Page<Blog> source = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        UserDTO currentUser = UserHolder.getUser();
        String currentUserId = currentUser == null || currentUser.getId() == null
                ? null : currentUser.getId().toString();
        List<BlogVO> records = new ArrayList<>(source.getRecords().size());
        for (Blog blog : source.getRecords()) {
            BlogVO vo = new BlogVO();
            BeanUtils.copyProperties(blog, vo);
            vo.setName(author.getNickName());
            vo.setIcon(author.getIcon());

            if (currentUserId == null) {
                vo.setIsLike(false);
            } else {
                Boolean isLiked = stringRedisTemplate.opsForSet().isMember(
                        RedisConstants.BLOG_LIKE_KEY + blog.getId(), currentUserId);
                if (isLiked == null) {
                    return Result.error("点赞状态获取失败");
                }
                vo.setIsLike(isLiked);
            }
            records.add(vo);
        }

        Page<BlogVO> resultPage = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        resultPage.setRecords(records);
        return Result.success(resultPage);
    }
}
