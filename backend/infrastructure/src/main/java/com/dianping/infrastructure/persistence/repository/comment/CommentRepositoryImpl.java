package com.dianping.infrastructure.persistence.repository.comment;

import com.dianping.application.comment.port.CommentReadPort;
import com.dianping.application.comment.query.CommentPage;
import com.dianping.application.comment.query.CommentRow;
import com.dianping.domain.comment.Comment;
import com.dianping.domain.comment.CommentRepository;
import com.dianping.domain.comment.CommentContent;
import com.dianping.application.comment.port.CommentLikeEligibilityPort;
import com.dianping.application.comment.port.CommentLikeMetadata;
import com.dianping.infrastructure.persistence.mapper.comment.CommentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianping.constant.SystemConstants;
import com.dianping.infrastructure.persistence.dataobject.comment.CommentDO;
import org.springframework.stereotype.Repository;

@Repository
public class CommentRepositoryImpl implements CommentRepository, CommentReadPort, CommentLikeEligibilityPort {
    private final CommentMapper mapper;

    public CommentRepositoryImpl(CommentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long insert(Comment comment) {
        CommentDO entity = new CommentDO().setBlogId(comment.blogId()).setUserId(comment.authorId())
                .setParentId(comment.parentId()).setAnswerId(comment.answerId())
                .setStatus(comment.status()).setContent(comment.content().value())
                .setLiked(0L).setLikeEpoch(1L).setLikeVersion(0L).setLikeInitialized(false);
        return mapper.insert(entity) > 0 ? entity.getId() : null;
    }

    @Override public Comment findById(Long id) {
        CommentDO entity = mapper.selectById(id);
        return entity == null ? null : Comment.restore(entity.getId(), entity.getBlogId(), entity.getUserId(),
                CommentContent.of(entity.getContent()), entity.getParentId(), entity.getAnswerId(), entity.getStatus());
    }
    @Override public CommentLikeMetadata metadata(Long id) {
        CommentDO entity = mapper.selectById(id);
        return entity == null ? null : metadataOf(entity);
    }
    public static CommentLikeMetadata metadataOf(CommentDO entity) {
        return new CommentLikeMetadata(entity.getLikeEpoch() == null ? 0 : entity.getLikeEpoch(),
                entity.getLikeVersion() == null ? 0 : entity.getLikeVersion(), entity.getLiked() == null ? 0 : entity.getLiked(),
                Boolean.TRUE.equals(entity.getLikeInitialized()));
    }

    @Override
    public CommentPage<CommentRow> query(Long blogId, int current) {
        Page<CommentDO> page = mapper.selectPage(new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                new LambdaQueryWrapper<CommentDO>().eq(CommentDO::getBlogId, blogId)
                        .eq(CommentDO::getParentId, 0)
                        .and(status -> status.eq(CommentDO::getStatus, 0).or().isNull(CommentDO::getStatus))
                        .orderByDesc(CommentDO::getCreateTime).orderByDesc(CommentDO::getId));
        return new CommentPage<>(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(entity -> new CommentRow(entity.getId(), entity.getBlogId(),
                        entity.getUserId(), entity.getContent(), entity.getCreateTime(), entity.getUpdateTime(),
                        entity.getLiked() == null ? 0 : entity.getLiked())).toList());
    }
}
