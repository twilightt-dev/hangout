package com.dianping.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dianping.dto.CreateCommentDTO;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.BlogComments;
import com.dianping.entity.User;
import com.dianping.mapper.BlogCommentsMapper;
import com.dianping.mapper.BlogMapper;
import com.dianping.result.Result;
import com.dianping.service.UserService;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BlogCommentsServiceImplTest {
    private final BlogCommentsServiceImpl service = new BlogCommentsServiceImpl();
    private final BlogCommentsMapper commentMapper = mock(BlogCommentsMapper.class);
    private final BlogMapper blogMapper = mock(BlogMapper.class);
    private final UserService userService = mock(UserService.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", commentMapper);
        ReflectionTestUtils.setField(service, "blogMapper", blogMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void createsCommentAndIncrementsBlogCount() {
        UserDTO currentUser = new UserDTO();
        currentUser.setId(7L);
        UserHolder.saveUser(currentUser);
        when(blogMapper.selectById(9L)).thenReturn(new Blog().setId(9L));
        doAnswer(invocation -> {
            BlogComments comment = invocation.getArgument(0);
            comment.setId(21L);
            return 1;
        }).when(commentMapper).insert((BlogComments) any());
        when(blogMapper.update(any(), any())).thenReturn(1);

        CreateCommentDTO request = new CreateCommentDTO();
        request.setContent("  很值得去  ");

        Result<Long> result = service.createComment(9L, request);

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getData()).isEqualTo(21L);
        verify(commentMapper).insert((BlogComments) argThat((BlogComments comment) ->
                comment.getBlogId().equals(9L)
                        && comment.getUserId().equals(7L)
                        && comment.getParentId().equals(0L)
                        && comment.getAnswerId().equals(0L)
                        && comment.getStatus().equals(0)
                        && comment.getContent().equals("很值得去")));
        verify(blogMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void rejectsCommentWhenUserIsAnonymous() {
        CreateCommentDTO request = new CreateCommentDTO();
        request.setContent("评论");

        Result<Long> result = service.createComment(9L, request);

        assertThat(result.getCode()).isEqualTo(0);
        verifyNoInteractions(commentMapper, blogMapper);
    }

    @Test
    void rejectsBlankOrOversizedContent() {
        UserDTO currentUser = new UserDTO();
        currentUser.setId(7L);
        UserHolder.saveUser(currentUser);
        CreateCommentDTO request = new CreateCommentDTO();
        request.setContent(" ");

        Result<Long> blankResult = service.createComment(9L, request);
        request.setContent("a".repeat(256));
        Result<Long> oversizedResult = service.createComment(9L, request);

        assertThat(blankResult.getCode()).isEqualTo(0);
        assertThat(oversizedResult.getCode()).isEqualTo(0);
        verifyNoInteractions(commentMapper, blogMapper);
    }

    @Test
    void queriesPagedCommentsAndBatchLoadsAuthors() {
        when(blogMapper.selectById(9L)).thenReturn(new Blog().setId(9L));
        BlogComments comment = new BlogComments().setId(21L).setBlogId(9L).setUserId(7L)
                .setContent("很好").setStatus(0);
        when(commentMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenAnswer(invocation -> {
            IPage<BlogComments> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(comment));
            return page;
        });
        when(userService.listByIds(List.of(7L)))
                .thenReturn(List.of(new User().setId(7L).setNickName("小林").setIcon("/avatar.png")));

        Result<IPage<?>> result = cast(service.queryComments(9L, 2));

        assertThat(result.getCode()).isEqualTo(1);
        IPage<?> page = result.getData();
        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getTotal()).isEqualTo(1);
        Object item = page.getRecords().get(0);
        assertThat(item).hasFieldOrPropertyWithValue("userName", "小林");
        assertThat(item).hasFieldOrPropertyWithValue("userIcon", "/avatar.png");
        verify(userService).listByIds(List.of(7L));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Result<IPage<?>> cast(Result result) {
        return result;
    }
}
