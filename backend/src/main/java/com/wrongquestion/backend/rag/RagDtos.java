package com.wrongquestion.backend.rag;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class RagDtos {
    private RagDtos() {}

    public record Ask(
            @NotBlank @Size(max = 2000) String question,
            Long rootId,
            String reviewStatus,
            Long focusQuestionId,
            boolean followUp) {}

    public record IndexCommand(Long rootId) {}

    public record Configuration(
            boolean generationEnabled,
            boolean demo,
            String model,
            boolean embeddingEnabled,
            String embeddingModel,
            int topK,
            int callsToday,
            int dailyCallLimit,
            int maxManualRetries) {}

    public record Session(String id, String title, Instant createdAt, Instant updatedAt) {}

    public record SessionPage(
            List<Session> items, int page, int size, long totalElements, long totalPages) {}

    public record Source(
            String reference,
            Long questionId,
            int chunkNumber,
            String questionPreview,
            String subject,
            List<String> knowledgePoints,
            String excerpt,
            double score,
            String method,
            String documentHash,
            LocalDateTime updatedAt,
            boolean available,
            boolean changed) {}

    public record Request(
            String id,
            String sessionId,
            String kind,
            String question,
            Long rootId,
            String reviewStatus,
            Long focusQuestionId,
            boolean followUp,
            String status,
            String phase,
            int retryCount,
            String answer,
            String answerType,
            List<String> citations,
            List<Source> sources,
            List<String> warnings,
            String retrievalMethod,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {}

    public record RequestPage(
            List<Request> items, int page, int size, long totalElements, long totalPages) {}

    public record IndexStatus(
            long questionCount,
            long indexedQuestionCount,
            long staleQuestionCount,
            long chunkCount,
            long embeddedChunkCount,
            boolean embeddingEnabled,
            String embeddingModel,
            int maxQuestions,
            int maxChunks) {}

    public record Result(
            String answer,
            String type,
            List<String> citations,
            List<Source> sources,
            List<String> warnings,
            String method) {}
}
