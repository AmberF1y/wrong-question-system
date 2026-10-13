package com.wrongquestion.backend.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AiDtos {
    private AiDtos() {}

    public record Knowledge(Long id, Long rootId, String path) {}

    public record Draft(
            String questionText,
            String correctAnswer,
            String analysis,
            List<Long> knowledgePointIds,
            List<String> warnings) {}

    public record Task(
            String id,
            String status,
            String fileName,
            String contentType,
            long imageSize,
            Long rootId,
            String provider,
            String model,
            int attemptCount,
            int retryCount,
            Draft draft,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant availableAt,
            Long questionId,
            Instant appliedAt) {}

    public record TaskPage(
            List<Task> items, int page, int size, long totalElements, long totalPages) {}

    public record Call(
            Long id,
            int attempt,
            String provider,
            String model,
            String status,
            String errorCode,
            Long inputTokens,
            Long outputTokens,
            Long durationMs,
            BigDecimal estimatedCost,
            Instant createdAt,
            Instant completedAt) {}

    public record CallEntry(
            String taskId,
            String fileName,
            Call call,
            String kind,
            String targetPath,
            String purpose) {}

    public record CallPage(
            List<CallEntry> items, int page, int size, long totalElements, long totalPages) {}

    public record Configuration(
            boolean enabled,
            boolean demo,
            String model,
            int dailyCallLimit,
            int callsToday,
            int concurrency,
            int maxAttempts,
            int maxManualRetries) {}

    public record Statistics(
            long taskCount,
            long activeCount,
            long succeededCount,
            long failedCount,
            long callCount,
            long failedCallCount,
            Long inputTokens,
            Long outputTokens,
            Double averageDurationMs,
            BigDecimal estimatedCost,
            Configuration configuration) {}
}
