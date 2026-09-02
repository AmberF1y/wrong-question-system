package com.wrongquestion.backend.question.dto;

import java.util.List;

public record QuestionPageResponse(
        List<QuestionSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
