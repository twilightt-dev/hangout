package com.dianping.domain.comment;

public record CommentContent(String value) {
    public CommentContent {
        if (value == null || value.trim().isEmpty()) {
            throw new CommentRuleViolation("评论内容不能为空");
        }
        value = value.trim();
        // 沿用 UTF-16 单元长度，不能改为 Unicode 码点数量。
        if (value.length() > 255) {
            throw new CommentRuleViolation("评论内容不能超过255个字符");
        }
    }

    public static CommentContent of(String raw) {
        return new CommentContent(raw);
    }
}
