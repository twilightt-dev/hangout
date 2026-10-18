package com.dianping.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 当前业务统一使用 service 模块中的 Mapper。 */
@Configuration(proxyBeanMethods = false)
@MapperScan({"com.dianping.mapper", "com.dianping.shopdetail.persistence.mapper"})
public class MapperConfiguration {
}
