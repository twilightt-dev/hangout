package com.dianping.infrastructure.persistence.adapter.comment;

import com.dianping.application.comment.like.sync.CommentLikeCountSnapshot;
import com.dianping.application.comment.port.CommentLikeCountSyncPort;
import com.dianping.infrastructure.persistence.dataobject.comment.CommentDO;
import com.dianping.infrastructure.persistence.mapper.comment.CommentMapper;
import com.dianping.infrastructure.redis.comment.CommentLikeRedisStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.List;

@Component
public class CommentLikeCountSyncAdapter implements CommentLikeCountSyncPort {
    private final CommentLikeRedisStore store;
    private final CommentMapper mapper;
    public CommentLikeCountSyncAdapter(CommentLikeRedisStore store, CommentMapper mapper) { this.store = store; this.mapper = mapper; }
    @Override public List<CommentLikeCountSnapshot> pending(int limit) { return store.pending(limit); }
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean writeCount(CommentLikeCountSnapshot snapshot) {
        if (snapshot.epoch() <= 0 || snapshot.version() <= 0 || snapshot.liked() < 0 || snapshot.liked() > 4294967295L) return false;
        if (mapper.projectLikeCount(snapshot.commentId(), snapshot.epoch(), snapshot.version(), snapshot.liked()) == 1) return true;
        CommentDO current = mapper.selectById(snapshot.commentId());
        return current != null && Boolean.TRUE.equals(current.getLikeInitialized())
                && Long.valueOf(snapshot.epoch()).equals(current.getLikeEpoch())
                && current.getLikeVersion() != null && current.getLikeVersion() >= snapshot.version();
    }
    @Override public void acknowledge(CommentLikeCountSnapshot snapshot) { store.acknowledge(snapshot); }
    @Override public void defer(CommentLikeCountSnapshot snapshot) { store.defer(snapshot); }
}
