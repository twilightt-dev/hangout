package com.dianping.feed;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FeedCacheTest {
    @Test void lostNonemptyTimelineTriggersRebuildWhileConfirmedEmptyTimelineRemainsReady() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("feed:state:5")).thenReturn("30:[1]");
        when(values.get("feed:state:5:empty")).thenReturn("0");
        when(redis.hasKey("feed:5")).thenReturn(false);
        FeedCache cache = new FeedCache(redis, new FeedProperties());
        assertThat(cache.ready(5, "30:[1]")).isFalse();
        when(redis.hasKey("feed:5")).thenReturn(true);
        assertThat(cache.ready(5, "30:[1]")).isTrue();
        when(values.get("feed:state:5:empty")).thenReturn("1");
        when(redis.hasKey("feed:5")).thenReturn(false);
        assertThat(cache.ready(5, "30:[1]")).isTrue();
        assertThat(cache.ready(5, "30:[1, 2]")).isFalse();
        when(values.get("feed:state:5:empty")).thenReturn(null);
        assertThat(cache.ready(5, "30:[1]")).isFalse();
    }
}
