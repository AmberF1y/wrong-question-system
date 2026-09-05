package com.wrongquestion.backend.question.image.dto;

public record QuestionImageResponse(
        Long questionId,
        String imagePath,
        String contentType,
        long size
) {
}
