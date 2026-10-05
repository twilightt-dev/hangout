package com.dianping.feed;

import com.dianping.entity.Blog;
import com.dianping.entity.FeedTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeedTaskWorkerTest {
    private final FeedTaskRepository tasks = mock(FeedTaskRepository.class);
    private final FeedRepository repository = mock(FeedRepository.class);
    private final FeedCache cache = mock(FeedCache.class);
    private final FeedProperties properties = new FeedProperties();
    private final FeedTaskWorker worker = new FeedTaskWorker(tasks, repository, cache, properties);
    private final FeedTask task = new FeedTask()
            .setId(1L).setKind("PUBLISH").setBlogId(9L).setAuthorId(2L)
            .setCursorId(0L).setAttempts(0);

    @BeforeEach void setup() {
        properties.setBatchSize(2);
        when(tasks.claim(any(), anyString(), anyLong())).thenReturn(true);
        when(repository.blog(9)).thenReturn(new Blog().setId(9L).setUserId(2L)
                .setCreateTime(FeedTimeline.time(System.currentTimeMillis() - 10000)));
    }

    @Test
    void partialRedisFailureRetainsCheckpointAndRestartReplaysIdempotently() {
        when(repository.fans(2, 0, 2)).thenReturn(List.of(10L, 20L));
        doThrow(new IllegalStateException("redis offline")).doNothing().when(cache).add(eq(20L), anyList(), anyLong());
        worker.process(task);
        verify(tasks).retry(eq(task), anyString(), any());
        verify(tasks, never()).progress(any(), anyString(), anyLong());
        new FeedTaskWorker(tasks, repository, cache, properties).process(task);
        verify(tasks).progress(eq(task), anyString(), eq(20L));
        verify(cache, times(2)).add(eq(10L), anyList(), anyLong());
    }

    @Test
    void hotAuthorCompletesWithoutFanout() {
        when(repository.hot(2, 1000)).thenReturn(true);
        worker.process(task);
        verify(tasks).finish(eq(task), anyString(), eq("DONE"), anyString());
        verify(repository, never()).fans(anyLong(), anyLong(), anyInt());
        verifyNoInteractions(cache);
    }

    @Test
    void expiredBlogStopsRetryingWithRecordedReason() {
        when(repository.blog(9)).thenReturn(new Blog().setId(9L).setCreateTime(FeedTimeline.time(1000)));
        worker.process(task);
        verify(tasks).finish(eq(task), anyString(), eq("EXPIRED"), eq("博客已超过展示窗口"));
        verifyNoInteractions(cache);
    }

    @Test
    void cleanupDoesNotEraseHistoryAfterUserRefollows() {
        var cleanup = new FeedTask()
                .setId(2L).setKind("CLEANUP").setAuthorId(2L).setUserId(5L)
                .setCursorId(0L).setAttempts(0);
        when(repository.follows(5, 2)).thenReturn(true);
        worker.process(cleanup);
        verify(cache).invalidate(5);
        verify(cache, never()).remove(anyLong(), anyCollection());
        verify(tasks).finish(eq(cleanup), anyString(), eq("DONE"), anyString());
    }
}
