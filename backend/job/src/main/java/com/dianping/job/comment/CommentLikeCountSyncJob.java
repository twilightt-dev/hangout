package com.dianping.job.comment;

import com.dianping.application.comment.like.sync.SyncCommentLikeCountsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class CommentLikeCountSyncJob {
    private static final Logger LOG = Logger.getLogger(CommentLikeCountSyncJob.class.getName());
    private final SyncCommentLikeCountsService service;
    private final boolean enabled;
    public CommentLikeCountSyncJob(SyncCommentLikeCountsService service,
                                  @Value("${comment-like.sync.enabled:true}") boolean enabled) {
        this.service = service; this.enabled = enabled;
    }
    @Scheduled(fixedDelayString = "${comment-like.sync.delay-ms:1000}")
    public void synchronize() {
        if (!enabled) return;
        try { service.synchronize(100); }
        catch (RuntimeException failure) { LOG.log(Level.WARNING, "评论点赞同步暂时失败，待同步记录保留", failure); }
    }
}
