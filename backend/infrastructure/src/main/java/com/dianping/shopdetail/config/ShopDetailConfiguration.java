package com.dianping.shopdetail.config;
import com.dianping.shopdetail.service.ShopDetailDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/** 领域服务由外层装配，领域模块不依赖 Spring。 */
@Configuration(proxyBeanMethods=false)
public class ShopDetailConfiguration {
    @Bean public ShopDetailDomainService shopDetailDomainService() { return new ShopDetailDomainService(); }
}
