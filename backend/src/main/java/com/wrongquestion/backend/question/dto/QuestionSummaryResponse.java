package com.wrongquestion.backend.question.dto;

import com.wrongquestion.backend.knowledge.dto.KnowledgePointResponse;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionSummaryResponse(
        Long id,
        String questionText,
        String subject,
        List<KnowledgePointResponse> knowledgePoints,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
