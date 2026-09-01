package com.wrongquestion.backend.knowledge.dto;

import java.util.List;

public record KnowledgePointTreeNodeResponse(
        Long id,
        String name,
        Long parentId,
        List<KnowledgePointTreeNodeResponse> children
) {
}
