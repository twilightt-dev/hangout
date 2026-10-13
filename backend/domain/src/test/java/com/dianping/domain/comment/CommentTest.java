package com.dianping.domain.comment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class CommentTest {
    // 防止遗漏空内容校验，或将 trim 改成不同的 Unicode 空白规则。
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void rejectsMissingContent(String raw) {
        assertThatThrownBy(() -> CommentContent.of(raw))
                .isInstanceOf(CommentRuleViolation.class).hasMessage("评论内容不能为空");
    }

    @Test
    void normalizesOnlyJavaTrimWhitespace() {
        assertThat(CommentContent.of(" \t很好\n ").value()).isEqualTo("很好");
        assertThat(CommentContent.of("\u2003").value()).isEqualTo("\u2003");
    }

    // 127 个代理对加一个字符是 255 个 UTF-16 单元；128 个代理对是 256。
    @Test
    void accepts255Utf16UnitsAfterTrim() {
        String content = "😀".repeat(127) + "好";
        assertThat(CommentContent.of(" " + content + " ").value()).isEqualTo(content);
    }

    @Test
    void rejects256Utf16UnitsEvenWithOnly128CodePoints() {
        assertThatThrownBy(() -> CommentContent.of("😀".repeat(128)))
                .isInstanceOf(CommentRuleViolation.class).hasMessage("评论内容不能超过255个字符");
    }

    @Test
    void createsOnlyNormalTopLevelCommentForTheGivenBlogAndAuthor() {
        Comment comment = Comment.create(9L, 7L, CommentContent.of("评论"));
        assertThat(comment.blogId()).isEqualTo(9L);
        assertThat(comment.authorId()).isEqualTo(7L);
        assertThat(comment.content().value()).isEqualTo("评论");
        assertThat(comment.parentId()).isZero();
        assertThat(comment.answerId()).isZero();
        assertThat(comment.status()).isZero();
    }

    @Test
    void rejectsInvalidOwnershipWhenCreatedDirectly() {
        assertThatThrownBy(() -> Comment.create(0L, 7L, CommentContent.of("评论")))
                .hasMessage("非法博客Id");
        assertThatThrownBy(() -> Comment.create(9L, null, CommentContent.of("评论")))
                .hasMessage("请先登录");
    }
}
