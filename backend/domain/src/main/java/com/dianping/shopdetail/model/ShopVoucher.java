package com.dianping.shopdetail.model;
import java.time.LocalDateTime;
/** 优惠券展示事实；交易资格最终由交易用例判断。 */
public record ShopVoucher(Long id, Long shopId, String title, String subTitle, String rules,
                          Long payValue, Long actualValue, Integer type, Integer stock,
                          LocalDateTime beginTime, LocalDateTime endTime, String availability) {}
