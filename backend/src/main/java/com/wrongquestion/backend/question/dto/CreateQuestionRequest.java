package com.wrongquestion.backend.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateQuestionRequest(
        @NotBlank(message = "题目内容不能为空")
        @Size(max = 10000, message = "题目内容不能超过10000个字符")
        String questionText,

        @NotBlank(message = "错误答案不能为空")
        @Size(max = 5000, message = "错误答案不能超过5000个字符")
        String wrongAnswer,

        @NotBlank(message = "正确答案不能为空")
        @Size(max = 5000, message = "正确答案不能超过5000个字符")
        String correctAnswer,

        @NotBlank(message = "题目解析不能为空")
        @Size(max = 10000, message = "题目解析不能超过10000个字符")
        String analysis,

        @NotBlank(message = "错误原因不能为空")
        @Size(max = 2000, message = "错误原因不能超过2000个字符")
        String errorReason,

        @NotEmpty(message = "至少选择一个知识点")
        List<@NotNull(message = "知识点ID不能为空") Long> knowledgePointIds
) {
}
