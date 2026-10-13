package com.dianping.application.comment.port;

import com.dianping.application.comment.like.sync.CommentLikeCountSnapshot;
import java.util.List;

public interface CommentLikeCountSyncPort {
    List<CommentLikeCountSnapshot> pending(int limit);
    boolean writeCount(CommentLikeCountSnapshot snapshot);
    void acknowledge(CommentLikeCountSnapshot snapshot);
    void defer(CommentLikeCountSnapshot snapshot);
}
