package com.dianping.application.comment.port;

public record CommentLikeMetadata(long epoch, long version, long liked, boolean initialized) {
    public CommentLikeMetadata(long epoch, long version, long liked) { this(epoch, version, liked, false); }
    public boolean enabled() { return epoch > 0; }
    public boolean canInitialize() { return enabled() && version == 0 && liked == 0 && !initialized; }
}
