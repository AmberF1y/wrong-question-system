package com.wrongquestion.backend.review.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReviewProperties.class)
public class ReviewTimeConfiguration {

    @Bean
    public Clock reviewClock(ReviewProperties properties) {
        return Clock.system(ZoneId.of(properties.getZoneId()));
    }
}
