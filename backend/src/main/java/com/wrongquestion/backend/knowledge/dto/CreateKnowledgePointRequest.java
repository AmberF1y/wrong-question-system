package com.wrongquestion.backend.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateKnowledgePointRequest(
        @NotBlank(message = "知识点名称不能为空")
        @Size(max = 100, message = "知识点名称不能超过100个字符")
        String name,
        Long parentId
) {
}
