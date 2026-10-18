-- 店铺套餐独立建表：仅创建缺失的表，不修改已有店铺或优惠券数据。
-- 金额以整数分存储；业务完整性由领域模型在读取时校验。
CREATE TABLE IF NOT EXISTS tb_shop_setmeal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shop_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    cover_image VARCHAR(1024) NOT NULL,
    price BIGINT NOT NULL,
    original_price BIGINT NULL,
    people INT NOT NULL,
    description VARCHAR(1000) NULL,
    rules VARCHAR(4000) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0下架，1上架',
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shop_setmeal_display (shop_id, status, sort, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tb_shop_setmeal_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    setmeal_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_setmeal_item_display (setmeal_id, sort, id),
    CONSTRAINT fk_setmeal_item_setmeal FOREIGN KEY (setmeal_id) REFERENCES tb_shop_setmeal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
