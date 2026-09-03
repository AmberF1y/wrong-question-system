package com.wrongquestion.backend.review.dto;

import java.time.LocalDate;

public record DueQuestionResponse(
        Long id,
        String questionText,
        String imagePath,
        String subject,
        LocalDate nextReviewDate
) {
}
