package com.dianping.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dianping.constant.RedisConstants;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.User;
import com.dianping.mapper.BlogMapper;
import com.dianping.result.Result;
import com.dianping.service.UserService;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BlogServiceImplTest {
    private final BlogServiceImpl service = new BlogServiceImpl();
    private final BlogMapper blogMapper = mock(BlogMapper.class);
    private final UserService userService = mock(UserService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", blogMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void anonymousHotPageIncludesAuthorsAndFalseLikeState() {
        Blog blog = new Blog().setId(8L).setUserId(3L).setLiked(12);
        User author = new User().setId(3L).setNickName("小林").setIcon("/imgs/user.jpg");
        when(userService.getById(3L)).thenReturn(author);
        stubPage(List.of(blog));

        Result<List<Blog>> result = service.pageQueryHotBlog(2);

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getData()).containsExactly(blog);
        assertThat(blog.getName()).isEqualTo("小林");
        assertThat(blog.getIcon()).isEqualTo("/imgs/user.jpg");
        assertThat(blog.getIsLike()).isFalse();
        verifyNoInteractions(redisTemplate);
        verify(blogMapper).selectPage(argThat(page -> page.getCurrent() == 2 && page.getSize() == 10),
                argThat(wrapper -> wrapper.getSqlSegment().contains("ORDER BY liked DESC")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loggedInHotPageIncludesCurrentUsersLikeState() {
        UserDTO currentUser = new UserDTO();
        currentUser.setId(5L);
        UserHolder.saveUser(currentUser);
        Blog blog = new Blog().setId(8L).setUserId(3L).setLiked(12);
        when(userService.getById(3L)).thenReturn(new User().setId(3L).setNickName("小林"));
        stubPage(List.of(blog));
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        when(sets.isMember(RedisConstants.BLOG_LIKE_KEY + 8, "5")).thenReturn(true);

        Result<List<Blog>> result = service.pageQueryHotBlog(1);

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getData().get(0).getIsLike()).isTrue();
    }

    @Test
    void invalidPageDoesNotQueryDatabase() {
        Result<List<Blog>> result = service.pageQueryHotBlog(0);

        assertThat(result.getCode()).isEqualTo(0);
        verifyNoInteractions(blogMapper);
    }

    @SuppressWarnings("unchecked")
    private void stubPage(List<Blog> blogs) {
        when(blogMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenAnswer(invocation -> {
            IPage<Blog> page = invocation.getArgument(0);
            page.setRecords(blogs);
            return page;
        });
    }
}
