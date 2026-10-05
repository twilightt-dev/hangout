package com.dianping.feed;

import com.dianping.dto.ScrollResult;
import com.dianping.entity.Blog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeedTimelineTest {
    private Blog blog(long id, long time) { return new Blog().setId(id).setCreateTime(FeedTimeline.time(time)); }

    @Test
    void mergedSameSecondPagesAreLexicographicDeduplicatedAndNeverSkipStaticItems() {
        List<Blog> merged = List.of(blog(10, 10000), blog(9, 10000), blog(80, 10000),
                blog(9, 10000), blog(2, 10000), blog(99, 9000), blog(77, 8000));
        long cursor = 10000;
        int offset = 0;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ScrollResult page = FeedTimeline.page(merged, cursor, offset, 2, 8500);
            for (Object item : page.getList()) ids.add(((Blog) item).getId());
            cursor = page.getMinTime(); offset = page.getOffset();
            if (page.getList().isEmpty()) break;
        }
        assertThat(ids).containsExactly(9L, 80L, 2L, 10L, 99L);
    }

    @Test
    void offsetAccumulatesOnlyWhenNextPageEndsAtTheSameTimestamp() {
        List<Blog> merged = List.of(blog(9, 10000), blog(8, 10000), blog(7, 10000), blog(6, 9000));
        ScrollResult sameTime = FeedTimeline.page(merged, 10000, 2, 1, 0);
        assertThat(sameTime.getOffset()).isEqualTo(3);
        ScrollResult older = FeedTimeline.page(merged, 10000, 3, 1, 0);
        assertThat(older.getMinTime()).isEqualTo(9000);
        assertThat(older.getOffset()).isEqualTo(1);
    }
}
