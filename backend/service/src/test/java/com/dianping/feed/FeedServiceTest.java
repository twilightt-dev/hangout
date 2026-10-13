package com.dianping.feed;

import com.dianping.dto.UserDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.User;
import com.dianping.service.UserService;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeedServiceTest {
    private final FeedRepository repository = mock(FeedRepository.class);
    private final FeedCache cache = mock(FeedCache.class);
    private final UserService users = mock(UserService.class);
    private final FeedProperties properties = new FeedProperties();
    private final FeedService service = new FeedService(repository, cache, properties, users);
    private long time;

    @BeforeEach
    void setup() {
        properties.setPageSize(2); properties.setBatchSize(2);
        UserDTO user = new UserDTO(); user.setId(5L); UserHolder.saveUser(user);
        time = System.currentTimeMillis() / 1000 * 1000 - 10000;
        when(repository.authors(5, 1000)).thenReturn(List.of(new FeedRepository.Author(1, false), new FeedRepository.Author(2, true)));
        when(cache.ready(eq(5L), anyString())).thenReturn(true);
        when(users.listByIds(anyCollection())).thenReturn(List.of(new User().setId(1L).setNickName("普通作者")));
    }

    @AfterEach void cleanup() { UserHolder.removeUser(); }
    private Blog blog(long id, long author) { return new Blog().setId(id).setUserId(author).setCreateTime(FeedTimeline.time(time)); }

    @Test
    void fillsPagePastDeletedAndUnfollowedCandidatesAndMergesHotAuthor() {
        when(cache.candidates(eq(5L), anyLong(), anyLong(), eq(0L), eq(2))).thenReturn(List.of(301L, 302L));
        when(cache.candidates(eq(5L), anyLong(), anyLong(), eq(2L), eq(2))).thenReturn(List.of(9L));
        when(repository.blogByIds(List.of(301L, 302L))).thenReturn(List.of(blog(301, 99)));
        when(repository.blogByIds(List.of(9L))).thenReturn(List.of(blog(9, 1)));
        when(repository.recent(eq(List.of(2L)), any(), any(), eq(2))).thenReturn(List.of(blog(80, 2)));

        var result = service.read(time + 1000, 0);
        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getData().getList()).extracting(item -> ((Blog) item).getId()).containsExactly(9L, 80L);
        assertThat(((Blog) result.getData().getList().get(0)).getName()).isEqualTo("普通作者");
    }

    @Test
    void redisFailureFallsBackToBothOrdinaryAndHotDatabaseBlogs() {
        when(cache.ready(eq(5L), anyString())).thenThrow(new IllegalStateException("redis offline"));
        when(repository.recent(eq(List.of(1L)), any(), any(), eq(2))).thenReturn(List.of(blog(9, 1)));
        when(repository.recent(eq(List.of(2L)), any(), any(), eq(2))).thenReturn(List.of(blog(80, 2)));
        assertThat(service.read(time + 1000, 0).getData().getList()).hasSize(2);
    }

    @Test
    void databaseFailureIsAnErrorNotAnEmptyFeed() {
        when(repository.authors(5, 1000)).thenThrow(new IllegalStateException("database offline"));
        assertThat(service.read(time, 0).getCode()).isZero();
    }

    @Test
    void missingOrChangedOrdinaryAuthorSignatureBackfillsHistoryBeforeReading() {
        when(cache.ready(eq(5L), anyString())).thenReturn(false);
        when(cache.beginBuild(5)).thenReturn("build-token");
        when(repository.history(eq(List.of(1L)), any(), eq(0L), eq(2))).thenReturn(List.of(blog(9, 1)));
        service.read(time + 1000, 0);
        verify(cache).add(eq(5L), argThat(blogs -> blogs.size() == 1 && blogs.get(0).getId() == 9), anyLong());
        verify(cache).complete(5, "30:[1]", "build-token");
    }

    @Test
    void invalidCursorOrAnonymousRequestDoesNotAccessStorage() {
        assertThat(service.read(time, -1).getCode()).isZero();
        UserHolder.removeUser();
        assertThat(service.read(time, 0).getCode()).isZero();
        verifyNoInteractions(repository, cache);
    }
}
