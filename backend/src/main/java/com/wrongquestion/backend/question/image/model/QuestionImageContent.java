package com.wrongquestion.backend.question.image.model;

import org.springframework.core.io.Resource;

public record QuestionImageContent(
        Resource resource,
        String contentType,
        long size
) {
}
