package com.wrongquestion.backend.review.dto;

public record DueReviewResponse(
        long dueCount,
        DueQuestionResponse question
) {
}
