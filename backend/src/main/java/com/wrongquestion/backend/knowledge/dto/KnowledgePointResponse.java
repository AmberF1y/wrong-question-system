package com.wrongquestion.backend.knowledge.dto;

public record KnowledgePointResponse(
        Long id,
        String name,
        Long parentId
) {
}
