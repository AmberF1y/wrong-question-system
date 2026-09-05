package com.wrongquestion.backend.question.image.model;

public record StoredQuestionImage(
        String relativePath,
        String contentType,
        long size
) {
}
