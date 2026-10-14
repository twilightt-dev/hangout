package com.dianping.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    private String secret;
    private Duration accessTtl = Duration.ofMinutes(5);
    private Duration refreshTtl = Duration.ofDays(7);

}
