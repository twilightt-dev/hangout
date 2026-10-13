package com.dianping.application.comment.like.sync;

import com.dianping.application.comment.port.CommentLikeCountSyncPort;
import org.springframework.stereotype.Service;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class SyncCommentLikeCountsService {
    private static final Logger LOG = Logger.getLogger(SyncCommentLikeCountsService.class.getName());
    private final CommentLikeCountSyncPort port;
    public SyncCommentLikeCountsService(CommentLikeCountSyncPort port) { this.port = port; }
    public void synchronize(int limit) {
        if (limit <= 0) return;
        for (CommentLikeCountSnapshot snapshot : port.pending(limit)) {
            try {
                if (port.writeCount(snapshot)) port.acknowledge(snapshot);
                else {
                    LOG.warning("评论点赞投影拒绝该代次或版本，保留待核对记录，评论=" + snapshot.commentId());
                    port.defer(snapshot);
                }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "评论点赞计数同步失败，评论=" + snapshot.commentId(), failure);
                try { port.defer(snapshot); }
                catch (RuntimeException deferredFailure) { LOG.log(Level.WARNING, "同步退避失败，保留待同步记录", deferredFailure); }
            }
        }
    }
}
