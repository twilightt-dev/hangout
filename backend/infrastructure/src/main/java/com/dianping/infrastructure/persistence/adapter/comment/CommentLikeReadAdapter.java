package com.dianping.infrastructure.persistence.adapter.comment;

import com.dianping.application.comment.like.CommentLikeUnavailableException;
import com.dianping.application.comment.port.*;
import com.dianping.infrastructure.persistence.dataobject.comment.CommentDO;
import com.dianping.infrastructure.persistence.mapper.comment.CommentMapper;
import com.dianping.infrastructure.persistence.repository.comment.CommentRepositoryImpl;
import com.dianping.infrastructure.redis.comment.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class CommentLikeReadAdapter implements CommentLikeReadPort {
    private final CommentMapper mapper;
    private final CommentLikeRedisStore store;
    private final RedisCommentLikeRepository repository;
    public CommentLikeReadAdapter(CommentMapper mapper, CommentLikeRedisStore store, RedisCommentLikeRepository repository) {
        this.mapper = mapper; this.store = store; this.repository = repository;
    }
    @Override public Map<Long, CommentLikeStatus> findStates(List<Long> ids, Long userId) {
        Map<Long, CommentLikeStatus> result = new HashMap<>();
        ids.forEach(id -> result.put(id, CommentLikeStatus.unavailable(userId)));
        Map<Long, CommentLikeMetadata> enabled = new LinkedHashMap<>();
        for (CommentDO entity : mapper.selectByIds(ids)) {
            CommentLikeMetadata metadata = CommentRepositoryImpl.metadataOf(entity);
            if (!metadata.enabled()) continue;
            try {
                if (metadata.canInitialize()) {
                    // 游客只触发空集合初始化，不读取任何真实用户关系。
                    repository.ensureInitialized(entity.getId(), metadata.epoch());
                }
                enabled.put(entity.getId(), metadata);
            } catch (CommentLikeUnavailableException unavailable) { result.put(entity.getId(), CommentLikeStatus.unavailable(userId)); }
        }
        try { result.putAll(store.readBatch(enabled, userId)); }
        catch (CommentLikeUnavailableException unavailable) { /* 保留 MySQL 数量与明确的未知状态。 */ }
        return result;
    }
}
