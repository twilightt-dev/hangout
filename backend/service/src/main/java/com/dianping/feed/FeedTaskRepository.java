package com.dianping.feed;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dianping.entity.FeedTask;
import com.dianping.entity.Follow;
import com.dianping.mapper.FeedTaskMapper;
import com.dianping.mapper.FollowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class FeedTaskRepository {
    private final FeedTaskMapper feedTaskMapper;
    private final FollowMapper followMapper;
    private final FeedProperties properties;
    public FeedTaskRepository(FeedTaskMapper tasks, FollowMapper follows, FeedProperties properties) {
        this.feedTaskMapper = tasks;
        this.followMapper = follows;
        this.properties = properties;
    }

    //在博客发布成功后调用
    public void publish(long blogId, long authorId) {
        enqueue("PUBLISH", blogId, authorId, null);
    }

    /**
     * 处理关注和取关
     * @param userId 用户id
     * @param authorId 作者id
     * @param followed true表示关注，false表示取关
     * 关注时发回填backfill任务，取关时发cleanup任务
     * 关注后刚好到阈值或者取关后刚到低于阈值发reclassify任务
     */
    public void relationship(long userId, long authorId, boolean followed) {
        enqueue(followed ? "BACKFILL" : "CLEANUP", null, authorId, userId);
        Long fans = followMapper.selectCount(Wrappers.<Follow>lambdaQuery().eq(Follow::getFollowUserId, authorId));
        if (fans != null && fans == (followed ? properties.threshold() : properties.threshold() - 1L)) {
            // 只在阈值边界分发失效通知，避免热点作者每新增一个粉丝都遍历所有粉丝。
            // 分类变化不依赖通知及时完成，读取时分类签名也会触发补齐。
            enqueue("RECLASSIFY", null, authorId, null);
        }
    }

    //创建并持久化feed任务
    private void enqueue(String kind, Long blogId, long authorId, Long userId) {
        FeedTask task = new FeedTask()
                .setKind(kind)
                .setBlogId(blogId)
                .setAuthorId(authorId)
                .setUserId(userId)
                .setStatus("PENDING")
                .setCursorId(0L)
                .setAttempts(0)
                .setNextAttempt(now());
        if (feedTaskMapper.insert(task) != 1) {
            throw new IllegalStateException("Feed 任务入队失败");
        }
    }
    //查询到期任务
    public List<FeedTask> due(int limit) {
        return feedTaskMapper.selectDue(Timestamp.from(Instant.now()), limit);
    }
    //认领任务
    public boolean claim(FeedTask task, String owner, long leaseSeconds) {
        Instant now = Instant.now();
        return feedTaskMapper.claim(task, owner, Timestamp.from(now),
                Timestamp.from(now.plusSeconds(Math.max(30, leaseSeconds)))) == 1;
    }
    //保存任务的处理进度
    public void progress(FeedTask task, String owner, long cursor) {
        requireOwner(feedTaskMapper.progress(task.getId(), owner, cursor, Timestamp.from(Instant.now())));
    }
    //结束任务
    public void finish(FeedTask task, String owner, String status, String reason) {
        requireOwner(feedTaskMapper.finish(task.getId(), owner, status, reason, Timestamp.from(Instant.now())));
    }
    //重试任务
    public void retry(FeedTask task, String owner, RuntimeException failure) {
        long delay = Math.min(300, 1L << Math.min(9, task.getAttempts() + 1));
        feedTaskMapper.retry(task.getId(), owner, failure.getClass().getSimpleName(), Timestamp.from(Instant.now().plusSeconds(delay)));
    }
    //获取UTC时间
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
    //通过执行sql之后影响的行数确认当前worker仍然持有租约
    private void requireOwner(int changed) {
        if (changed != 1) throw new IllegalStateException("Feed 任务租约已被其他消费者接管");
    }
}
