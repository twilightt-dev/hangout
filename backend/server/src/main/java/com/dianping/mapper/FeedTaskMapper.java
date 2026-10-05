package com.dianping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianping.entity.FeedTask;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

/** Feed 任务的持久化语句定义于 mapper/FeedTaskMapper.xml。 */
public interface FeedTaskMapper extends BaseMapper<FeedTask> {
    List<FeedTask> selectDue(@Param("now") Timestamp now, @Param("limit") int limit);

    int claim(@Param("task") FeedTask task, @Param("owner") String owner,
            @Param("now") Timestamp now, @Param("leaseUntil") Timestamp leaseUntil);

    int progress(@Param("taskId") long taskId, @Param("owner") String owner,
            @Param("cursor") long cursor, @Param("now") Timestamp now);

    int finish(@Param("taskId") long taskId, @Param("owner") String owner, @Param("status") String status,
            @Param("reason") String reason, @Param("now") Timestamp now);

    int retry(@Param("taskId") long taskId, @Param("owner") String owner,
            @Param("error") String error, @Param("nextAttempt") Timestamp nextAttempt);
}
