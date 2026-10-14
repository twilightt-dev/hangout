package com.dianping.feed;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "feed")
public class FeedProperties {
    private int retentionDays = 30; //最多保存时间
    private int hotAuthorThreshold = 1000; //热点作者阈值
    private int pageSize = 10; //每页多少篇
    private int batchSize = 200; //一批处理多少
    private long leaseSeconds = 300; //后台消费者认领任务之后占用该任务的有效时间

    public int retentionDays() { return Math.max(1, retentionDays); }
    public int threshold() { return Math.max(1, hotAuthorThreshold); }
    public int pageSize() { return Math.max(1, Math.min(100, pageSize)); }
    public int batchSize() { return Math.max(1, Math.min(1000, batchSize)); }
}
