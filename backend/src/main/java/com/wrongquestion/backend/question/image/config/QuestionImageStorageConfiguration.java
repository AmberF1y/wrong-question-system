package com.wrongquestion.backend.question.image.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QuestionImageStorageProperties.class)
public class QuestionImageStorageConfiguration {
}
