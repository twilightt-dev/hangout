package com.dianping.domain.comment;

public class CommentRuleViolation extends RuntimeException {
    public CommentRuleViolation(String message) {
        super(message);
    }
}
