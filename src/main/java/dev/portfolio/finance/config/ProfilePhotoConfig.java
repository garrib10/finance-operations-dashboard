package dev.portfolio.finance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProfilePhotoProperties.class)
public class ProfilePhotoConfig {
}
