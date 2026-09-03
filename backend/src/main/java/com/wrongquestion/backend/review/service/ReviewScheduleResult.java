package com.wrongquestion.backend.review.service;

import com.wrongquestion.backend.review.entity.ReviewStatus;

import java.time.LocalDate;

public record ReviewScheduleResult(
        ReviewStatus reviewStatus,
        LocalDate nextReviewDate,
        int consecutiveProficientCount
) {
}
