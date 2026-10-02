package com.dianping;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.dianping.config.UploadProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.dianping.mapper")
@SpringBootApplication
@EnableConfigurationProperties(UploadProperties.class)
@EnableAspectJAutoProxy(exposeProxy = true)
public class CommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentApplication.class, args);
    }

}
