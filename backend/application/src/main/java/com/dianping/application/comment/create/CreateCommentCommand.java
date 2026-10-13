package com.dianping.application.comment.create;

public record CreateCommentCommand(Long blogId, String content) {}
