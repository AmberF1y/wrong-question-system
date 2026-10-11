package com.wrongquestion.backend.ai;

import jakarta.validation.constraints.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties("app.ai")
public record AiProperties(
        @NotBlank String mode,
        String baseUrl,
        String apiKey,
        @NotBlank @Size(max = 100) String model,
        @Min(1) @Max(300) int timeoutSeconds,
        @Min(1) @Max(4) int concurrency,
        @Min(1) @Max(5) int maxAttempts,
        @Min(0) @Max(5) int maxManualRetries,
        @Min(1) @Max(10000) int dailyCallLimit,
        boolean workerEnabled,
        @Min(1) @Max(4096) int maxKnowledgePoints,
        String responseFormat,
        @DecimalMin("0") BigDecimal inputPricePerMillion,
        @DecimalMin("0") BigDecimal outputPricePerMillion) {}
