package com.dianping.shop.shopdetail.model;


import com.dianping.shop.shopdetail.service.ShopDetailDomainService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 周边相关店铺值对象
 *  <p>
 *  以当前店铺为中心的五公里内同类型店铺，距离单位为米。
 *  由 {@link ShopDetailDomainService} 限定业务规则。
 *  </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopRelatedShop {

    /**
     * 店铺标识。
     */
    private Long shopId;
    /**
     * 店铺名称。
     */
    private String shopName;
    /**
     * 所属商圈。
     */
    private String area;
    /**
     * 店铺人均价格。
     */
    private Long avgPrice;
    /**
     * 按实际分数的十倍保存的评分。
     */
    private Integer score;
    /**
     * 距当前店铺的球面直线距离，单位为米。
     */
    private Double distance;
    /**
     * 周边推荐说明。
     */
    private String recommendationReason;

}
