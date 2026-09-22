package com.wrongquestion.backend.review.dto;

public record MasteredSpotCheckResponse(
        boolean completedToday,
        long eligibleCount,
        int cooldownDays,
        SpotCheckQuestionResponse question
) {
}
