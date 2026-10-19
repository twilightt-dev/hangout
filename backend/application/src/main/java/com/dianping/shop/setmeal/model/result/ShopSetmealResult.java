package com.dianping.shop.setmeal.model.result;

import com.dianping.shopdetail.model.ShopSetmeal;
import com.dianping.shopdetail.model.ShopSetmealItem;
import java.util.List;

/**
 * 套餐展示结果，不暴露上下架状态等内部管理信息。
 *
 * @param id 记录标识
 * @param shopId 店铺标识
 * @param name 名称
 * @param coverImage 已上传的封面图片路径
 * @param price 套餐支付金额，单位为分
 * @param originalPrice 套餐原价，单位为分
 * @param people 适用人数
 * @param description 说明文本
 * @param rules 使用规则
 * @param items 套餐包含的固定菜品明细
 */
public record ShopSetmealResult(Long id, Long shopId, String name, String coverImage, Long price,
                                Long originalPrice, Integer people, String description, String rules,
                                List<ShopSetmealItem> items) {
    /**
     * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
     *
     * @param setmeal 套餐业务数据
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public static ShopSetmealResult from(ShopSetmeal setmeal) {
        return new ShopSetmealResult(setmeal.id(), setmeal.shopId(), setmeal.name(), setmeal.coverImage(),
                setmeal.price(), setmeal.originalPrice(), setmeal.people(), setmeal.description(),
                setmeal.rules(), setmeal.items());
    }
}
