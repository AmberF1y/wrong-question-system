package com.wrongquestion.backend.performance.dto;

public record PerformanceIdentityResponse(
        String mode,
        String environmentId,
        String databaseName,
        String imageDirectory,
        String gitCommit
) {
}
