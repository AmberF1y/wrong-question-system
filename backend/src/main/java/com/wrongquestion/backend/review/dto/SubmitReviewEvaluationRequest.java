package com.wrongquestion.backend.review.dto;

import com.wrongquestion.backend.review.entity.ReviewRating;
import jakarta.validation.constraints.NotNull;

public record SubmitReviewEvaluationRequest(
        @NotNull(message = "复习评价不能为空")
        ReviewRating rating
) {
}
