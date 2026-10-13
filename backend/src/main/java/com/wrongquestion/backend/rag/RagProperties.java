package com.wrongquestion.backend.rag;

import jakarta.validation.constraints.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties("app.rag")
public record RagProperties(
        @NotBlank @Size(max = 100) String model,
        boolean embeddingEnabled,
        @Size(max = 100) String embeddingModel,
        @Min(1) @Max(4) int concurrency,
        @Min(100) @Max(10000) int maxQuestions,
        @Min(100) @Max(20000) int maxChunks,
        @Min(300) @Max(1200) int chunkSize,
        @Min(0) @Max(200) int chunkOverlap,
        @Min(1) @Max(8) int topK,
        @Min(1) @Max(32) int embeddingBatchSize,
        @Min(1) @Max(5) int embeddingBatchesPerRequest,
        @DecimalMin("0") @DecimalMax("1") double minimumSimilarity,
        @DecimalMin("0") BigDecimal inputPricePerMillion,
        @DecimalMin("0") BigDecimal outputPricePerMillion,
        @DecimalMin("0") BigDecimal embeddingPricePerMillion) {}
