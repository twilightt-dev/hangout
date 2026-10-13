package com.dianping.application.comment.port;

import java.util.List;
import java.util.Map;

public interface CommentLikeReadPort { Map<Long, CommentLikeStatus> findStates(List<Long> ids, Long userId); }
