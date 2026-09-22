package com.wrongquestion.backend.review.dto;

public record SpotCheckQuestionResponse(
        Long id,
        String questionText,
        String imagePath,
        String subject
) {
}
