-- 推荐菜与用户推荐关系独立建表；不修改店铺、评价、套餐及笔记数据。
-- 执行前请核对目标数据库；已有表不会自动升级，请先比对结构。
CREATE TABLE IF NOT EXISTS tb_recommend_dish (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜品标识',
    shop_id BIGINT UNSIGNED NOT NULL COMMENT '所属店铺',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '创建者',
    request_id VARCHAR(36) NOT NULL COMMENT '创建提交标识，支持重试',
    name VARCHAR(100) NOT NULL COMMENT '去除首尾空白后的菜名',
    name_key VARBINARY(400) NOT NULL COMMENT '规范化菜名的UTF-8字节，用于精确去重',
    cover_image VARCHAR(1024) NOT NULL COMMENT '已上传的一张封面图片路径',
    recommend_count BIGINT NOT NULL DEFAULT 0 COMMENT '不同推荐用户的人数',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recommend_dish_name (shop_id, name_key),
    UNIQUE KEY uk_recommend_dish_submission (user_id, request_id),
    KEY idx_recommend_dish_display (shop_id, recommend_count, create_time, id),
    CONSTRAINT ck_recommend_dish_count CHECK (recommend_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tb_recommend_dish_user (
    dish_id BIGINT NOT NULL COMMENT '推荐菜标识',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '推荐用户',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (dish_id, user_id),
    CONSTRAINT fk_recommend_dish_user_dish FOREIGN KEY (dish_id) REFERENCES tb_recommend_dish (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
