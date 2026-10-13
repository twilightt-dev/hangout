package com.dianping.application.comment.query;

import java.util.List;

public record CommentPage<T>(long current, long size, long total, List<T> records) {}
