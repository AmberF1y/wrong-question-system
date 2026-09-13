package com.wrongquestion.backend.performance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("performance")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PerformanceSafetyProperties.class)
public class PerformanceSafetyConfiguration {
}
