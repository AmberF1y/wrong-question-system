package com.wrongquestion.backend.review.dto;

import com.wrongquestion.backend.review.entity.ReviewEventType;
import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewStatus;

import java.time.Instant;
import java.time.LocalDate;

public record ReviewActionResponse(
        Long questionId,
        ReviewEventType eventType,
        ReviewRating rating,
        Instant occurredAt,
        ReviewStatus reviewStatus,
        LocalDate nextReviewDate,
        int consecutiveProficientCount,
        Instant lastReviewedAt
) {
}
