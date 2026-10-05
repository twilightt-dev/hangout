-- 增量迁移：仅新增表和索引，不包含 DROP/TRUNCATE 或现有数据修改。
-- 在当前业务数据库执行。重复执行时已有对象会跳过。
CREATE TABLE IF NOT EXISTS tb_feed_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    kind VARCHAR(24) NOT NULL,
    blog_id BIGINT UNSIGNED NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    cursor_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt DATETIME(3) NOT NULL,
    lease_owner VARCHAR(36) NULL,
    lease_until DATETIME(3) NULL,
    last_error VARCHAR(255) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_feed_task_due (status,next_attempt,id),
    KEY idx_feed_task_lease (status,lease_until,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @feed_blog_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='tb_blog' AND index_name='idx_blog_author_time'),
    'SELECT 1', 'ALTER TABLE tb_blog ADD INDEX idx_blog_author_time(user_id,create_time,id)');
PREPARE feed_index_statement FROM @feed_blog_index_sql;
EXECUTE feed_index_statement;
DEALLOCATE PREPARE feed_index_statement;

SET @feed_fans_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='tb_follow' AND index_name='idx_follow_author_fan'),
    'SELECT 1', 'ALTER TABLE tb_follow ADD INDEX idx_follow_author_fan(follow_user_id,user_id)');
PREPARE feed_index_statement FROM @feed_fans_index_sql;
EXECUTE feed_index_statement;
DEALLOCATE PREPARE feed_index_statement;
