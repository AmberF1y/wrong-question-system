package com.wrongquestion.backend.review.controller;

import com.wrongquestion.backend.review.dto.DueReviewResponse;
import com.wrongquestion.backend.review.dto.ReviewActionResponse;
import com.wrongquestion.backend.review.dto.SubmitReviewEvaluationRequest;
import com.wrongquestion.backend.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/due/next")
    public DueReviewResponse getNextDue(
            @RequestParam(required = false) String subject
    ) {
        return reviewService.getNextDue(subject);
    }

    @PostMapping("/{questionId}/evaluations")
    public ReviewActionResponse evaluate(
            @PathVariable Long questionId,
            @Valid @RequestBody SubmitReviewEvaluationRequest request
    ) {
        return reviewService.evaluate(questionId, request.rating());
    }

    @PostMapping("/{questionId}/reactivate")
    public ReviewActionResponse reactivate(@PathVariable Long questionId) {
        return reviewService.reactivate(questionId);
    }
}
