package com.dianping.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 过渡期同时装配遗留 Mapper 与新基础设施 Mapper。 */
@Configuration(proxyBeanMethods = false)
@MapperScan({"com.dianping.mapper", "com.dianping.infrastructure.persistence.mapper"})
public class MapperConfiguration {
}
