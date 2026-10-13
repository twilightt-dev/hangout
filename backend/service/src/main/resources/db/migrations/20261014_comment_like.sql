-- 增量 DDL：先核对备份并由部署人员执行。本文件不自动执行，不修改历史 liked。
-- like_epoch=0 保持历史评论暂停点赞；只能在关系核对后单独启用。
ALTER TABLE tb_blog_comments
    ADD COLUMN like_epoch BIGINT NOT NULL DEFAULT 0 COMMENT '点赞启用代次，0为未核对',
    ADD COLUMN like_version BIGINT NOT NULL DEFAULT 0 COMMENT '已应用计数版本',
    ADD COLUMN like_initialized BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已完成Redis初始化';
