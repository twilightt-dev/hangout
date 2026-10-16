-- 店铺评价增量迁移：执行前请在目标 MySQL 环境核对版本和表结构；本文件不删除已有数据。
CREATE TABLE IF NOT EXISTS tb_shop_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shop_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    overall_score TINYINT NOT NULL,
    taste_score TINYINT NOT NULL,
    environment_score TINYINT NOT NULL,
    service_score TINYINT NOT NULL,
    content VARCHAR(4000) NOT NULL,
    tags VARCHAR(2000) NOT NULL,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_review_submission (user_id, request_id),
    KEY idx_shop_review_page (shop_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

