package com.dianping.shopdetail.config;
import com.dianping.shop.shopdetail.repository.*;
import com.dianping.shop.shopdetail.service.ShopDetailDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
/**
 * 领域服务由外层装配，领域模块不依赖 Spring。
 */
@Configuration(proxyBeanMethods=false)
@EnableTransactionManagement
public class ShopDetailConfiguration {
    /**
     * 通过仓储接口装配纯 Java 领域服务，领域层不依赖 Spring。
     *
     * @param details 店铺基础资料、探店语料和周边查询仓储
     * @param vouchers 优惠券读取结果
     * @param meals 套餐仓储
     * @param reviews 评价读取结果
     * @param dishes 推荐菜及用户推荐关系仓储
     * @return 通过仓储接口装配的商详领域服务
     */
    @Bean public ShopDetailDomainService shopDetailDomainService(ShopDetailRepository details,
                                                                 ShopVoucherRepository vouchers, ShopSetmealRepository meals, ShopReviewRepository reviews,
                                                                 RecommendDishRepository dishes) {
        return new ShopDetailDomainService(details, vouchers, meals, reviews, dishes);
    }
}
