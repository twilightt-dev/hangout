package com.dianping.infrastructure.redis.comment;

import com.dianping.application.comment.like.CommentLikeUnavailableException;
import com.dianping.application.comment.port.CommentLikeMetadata;
import com.dianping.domain.comment.*;
import com.dianping.infrastructure.persistence.dataobject.comment.CommentDO;
import com.dianping.infrastructure.persistence.mapper.comment.CommentMapper;
import com.dianping.infrastructure.persistence.repository.comment.CommentRepositoryImpl;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class RedisCommentLikeRepository implements CommentLikeRepository {
    private final CommentLikeRedisStore store;
    private final CommentMapper mapper;
    private final TransactionTemplate initialization;
    public RedisCommentLikeRepository(CommentLikeRedisStore store, CommentMapper mapper, PlatformTransactionManager transactions) {
        this.store = store; this.mapper = mapper;
        initialization = new TransactionTemplate(transactions);
        initialization.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public CommentLike findState(CommentLikeId id, long epoch, boolean allowInitialize) {
        if (!allowInitialize) return store.read(id, epoch, false);
        ensureInitialized(id.commentId(), epoch);
        return store.read(id, epoch, false);
    }
    public void ensureInitialized(Long commentId, long epoch) {
        // 持有数据库行锁直到初始化标记提交，避免并发请求拿着过期许可重建已经使用过的关系。
        initialization.executeWithoutResult(status -> {
            CommentDO entity = mapper.lockForLikeInitialization(commentId);
            if (entity == null) throw new CommentLikeUnavailableException();
            CommentLikeMetadata current = CommentRepositoryImpl.metadataOf(entity);
            if (current.epoch() != epoch || !current.enabled()) throw new CommentLikeUnavailableException();
            store.initialize(commentId, epoch, current.canInitialize());
            if (!current.initialized() && mapper.markLikeInitialized(commentId, epoch) != 1) throw new CommentLikeUnavailableException();
        });
    }
    @Override public CommentLikeApplyResult tryApply(CommentLikeChange change, long epoch) { return store.apply(change, epoch); }
}
