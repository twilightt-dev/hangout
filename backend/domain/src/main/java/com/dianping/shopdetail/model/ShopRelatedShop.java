package com.dianping.shopdetail.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 周边相关店铺值对象
 * <p>
 * 以当前店铺为中心的五公里内同类型店铺，距离单位为米。
 * 由 {@link com.dianping.shopdetail.service.ShopDetailDomainService} 限定业务规则。
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopRelatedShop {

    private Long shopId;
    private String shopName;
    private String area;
    private Long avgPrice;
    private Integer score;
    private Double distance;
    private String recommendationReason;

}
