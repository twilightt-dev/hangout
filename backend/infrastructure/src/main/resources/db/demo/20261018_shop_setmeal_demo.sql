-- 仅用于开发联调，先执行套餐建表脚本，再在目标 MySQL 中单独执行本文件。
-- 默认选择 ID 最小的已有店铺，可将下一行替换为明确的目标店铺 ID。
-- 套餐名称带有演示标识；不会修改已有套餐，重复执行不会重复插入。
SET @setmeal_demo_shop_id = (SELECT MIN(id) FROM tb_shop);

START TRANSACTION;

INSERT INTO tb_shop_setmeal (shop_id, name, cover_image, price, original_price, people, description, rules, status, sort)
SELECT s.id, '【演示】99元双人餐', SUBSTRING_INDEX(s.images, ',', 1), 9900, 12900, 2,
       '固定菜品组合，仅供功能演示', CONCAT('无需预约', CHAR(10), '使用前请与门店确认营业时间'), 1, 10
FROM tb_shop s
WHERE s.id = @setmeal_demo_shop_id
  AND s.images IS NOT NULL AND TRIM(s.images) <> ''
  AND NOT EXISTS (SELECT 1 FROM tb_shop_setmeal m WHERE m.shop_id = s.id AND m.name = '【演示】99元双人餐');

INSERT INTO tb_shop_setmeal (shop_id, name, cover_image, price, original_price, people, description, rules, status, sort)
SELECT s.id, '【演示】四人分享餐', SUBSTRING_INDEX(s.images, ',', 1), 19900, NULL, 4,
       NULL, '菜品为固定组合，不支持替换。', 1, 20
FROM tb_shop s
WHERE s.id = @setmeal_demo_shop_id
  AND s.images IS NOT NULL AND TRIM(s.images) <> ''
  AND NOT EXISTS (SELECT 1 FROM tb_shop_setmeal m WHERE m.shop_id = s.id AND m.name = '【演示】四人分享餐');

INSERT INTO tb_shop_setmeal (shop_id, name, cover_image, price, original_price, people, description, rules, status, sort)
SELECT s.id, '【演示】已下架套餐', SUBSTRING_INDEX(s.images, ',', 1), 5900, NULL, 1,
       NULL, '仅供验证下架套餐不对外展示。', 0, 0
FROM tb_shop s
WHERE s.id = @setmeal_demo_shop_id
  AND s.images IS NOT NULL AND TRIM(s.images) <> ''
  AND NOT EXISTS (SELECT 1 FROM tb_shop_setmeal m WHERE m.shop_id = s.id AND m.name = '【演示】已下架套餐');

INSERT INTO tb_shop_setmeal_item (setmeal_id, name, quantity, unit, sort)
SELECT m.id, d.name, d.quantity, d.unit, d.sort
FROM tb_shop_setmeal m
JOIN (
    SELECT '【演示】99元双人餐' AS meal_name, '烤鱼' AS name, 1.00 AS quantity, '份' AS unit, 10 AS sort
    UNION ALL SELECT '【演示】99元双人餐', '米饭', 2.00, '碗', 20
    UNION ALL SELECT '【演示】99元双人餐', '饮料', 2.00, '杯', 30
    UNION ALL SELECT '【演示】四人分享餐', '主菜拼盘', 1.00, '份', 10
    UNION ALL SELECT '【演示】四人分享餐', '时令蔬菜', 1.50, '份', 20
    UNION ALL SELECT '【演示】四人分享餐', '米饭', 4.00, '碗', 30
    UNION ALL SELECT '【演示】已下架套餐', '单人主食', 1.00, '份', 10
) d ON m.name = d.meal_name
WHERE m.shop_id = @setmeal_demo_shop_id
  AND NOT EXISTS (SELECT 1 FROM tb_shop_setmeal_item i WHERE i.setmeal_id = m.id AND i.name = d.name);

COMMIT;

-- 执行后应得到两个上架套餐、一个下架套餐；无店铺图片时不生成演示套餐。
SELECT id, shop_id, name, status, sort FROM tb_shop_setmeal
WHERE shop_id = @setmeal_demo_shop_id AND name LIKE '【演示】%' ORDER BY sort, id;
