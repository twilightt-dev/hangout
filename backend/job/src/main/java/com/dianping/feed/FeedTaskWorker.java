package com.dianping.feed;

import com.dianping.entity.Blog;
import com.dianping.entity.FeedTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class FeedTaskWorker {
    private final FeedTaskRepository feedTaskRepository;
    private final FeedRepository feedRepository;
    private final FeedCache cache;
    private final FeedProperties properties;

    public FeedTaskWorker(FeedTaskRepository tasks, FeedRepository repository, FeedCache cache, FeedProperties properties) {
        this.feedTaskRepository = tasks;
        this.feedRepository = repository;
        this.cache = cache;
        this.properties = properties;
    }

    //定时扫描过期任务处理
    @Scheduled(fixedDelayString = "${feed.poll-interval-ms:1000}", initialDelayString = "${feed.initial-delay-ms:5000}")
    public void poll() {
        try {
            for (FeedTask task : feedTaskRepository.due(20)) process(task);
        } catch (RuntimeException failure) {
            log.error("Feed 任务扫描失败，请检查迁移及数据库连接", failure);
        }
    }

    //执行任务
    void process(FeedTask task) {
        String owner = UUID.randomUUID().toString();
        if (!feedTaskRepository.claim(task, owner, properties.getLeaseSeconds())) return;
        long lower = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS).toEpochMilli();
        try {//根据不同情况调用不同的方法
            switch (task.getKind()) {
                case "PUBLISH" -> publish(task, owner, lower);
                case "BACKFILL" -> {
                    // 读取时同步按关系签名补齐历史；异步失效也处理重新关注及竞争情况。
                    cache.invalidate(task.getUserId());
                    feedTaskRepository.finish(task, owner, "DONE", null);
                }
                case "CLEANUP" -> cleanup(task, owner, lower);
                case "RECLASSIFY" -> {
                    List<Long> fans = feedRepository.fans(task.getAuthorId(), task.getCursorId(), properties.batchSize());
                    for (Long fan : fans) cache.invalidate(fan);
                    advance(task, owner, fans);
                }
                default -> feedTaskRepository.finish(task, owner, "FAILED", "未知任务类型");
            }
        } catch (RuntimeException failure) {
            log.warn("Feed 任务失败，将退避重试，taskId={},kind={}", task.getId(), task.getKind());
            feedTaskRepository.retry(task, owner, failure);
        }
    }

    private void publish(FeedTask task, String owner, long lower) {
        Blog blog = feedRepository.blog(task.getBlogId());
        if (blog == null || FeedTimeline.score(blog) < lower) {
            feedTaskRepository.finish(task, owner, "EXPIRED", blog == null ? "博客不存在" : "博客已超过展示窗口");
            return;
        }
        if (feedRepository.hot(task.getAuthorId(), properties.threshold())) {
            feedTaskRepository.finish(task, owner, "DONE", "热点作者在读取时拉取");
            return;
        }
        List<Long> fans = feedRepository.fans(task.getAuthorId(), task.getCursorId(), properties.batchSize());
        for (Long fan : fans) cache.add(fan, List.of(blog), lower);
        advance(task, owner, fans);
    }

    private void advance(FeedTask task, String owner, List<Long> fans) {
        if (fans.size() < properties.batchSize()) feedTaskRepository.finish(task, owner, "DONE", null);
        else feedTaskRepository.progress(task, owner, fans.get(fans.size() - 1));
    }

    private void cleanup(FeedTask task, String owner, long lower) {
        if (feedRepository.follows(task.getUserId(), task.getAuthorId())) {
            cache.invalidate(task.getUserId());
            feedTaskRepository.finish(task, owner, "DONE", "用户已重新关注，跳过清理");
            return;
        }
        List<Blog> blogs = feedRepository.history(List.of(task.getAuthorId()), FeedTimeline.time(lower), task.getCursorId(), properties.batchSize());
        // 先失效再清理、清理后再失效，避免与重新关注的重建竞争留下错误完成标记。
        cache.invalidate(task.getUserId());
        cache.remove(task.getUserId(), blogs.stream().map(Blog::getId).toList());
        cache.prune(task.getUserId(), lower);
        cache.invalidate(task.getUserId());
        if (blogs.size() < properties.batchSize()) feedTaskRepository.finish(task, owner, "DONE", null);
        else feedTaskRepository.progress(task, owner, blogs.get(blogs.size() - 1).getId());
    }
}
