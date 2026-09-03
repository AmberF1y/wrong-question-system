package com.wrongquestion.backend.question.dto;

import com.wrongquestion.backend.knowledge.dto.KnowledgePointResponse;
import com.wrongquestion.backend.review.entity.ReviewStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record QuestionDetailResponse(
        Long id,
        String questionText,
        String wrongAnswer,
        String correctAnswer,
        String analysis,
        String errorReason,
        String subject,
        String imagePath,
        List<KnowledgePointResponse> knowledgePoints,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        ReviewStatus reviewStatus,
        LocalDate nextReviewDate,
        int consecutiveProficientCount,
        Instant lastReviewedAt
) {
}
