package com.dianping.shop.shopdetail.model;
import java.time.LocalDateTime;
/**
 * 优惠券展示事实；交易资格最终由交易用例判断。
 *
 * @param id 记录标识
 * @param shopId 店铺标识
 * @param title 标题
 * @param subTitle 副标题
 * @param rules 使用规则
 * @param payValue 支付金额，单位为分
 * @param actualValue 抵扣金额，单位为分
 * @param type 优惠券类型
 * @param stock 秒杀券剩余库存
 * @param beginTime 秒杀开始时间
 * @param endTime 秒杀结束时间
 * @param availability 可购买、未开始或售罄的展示状态
 */
public record ShopVoucher(Long id, Long shopId, String title, String subTitle, String rules,
                          Long payValue, Long actualValue, Integer type, Integer stock,
                          LocalDateTime beginTime, LocalDateTime endTime, String availability) {}
