package com.wrongquestion.backend.review.service;

import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewSchedulingPolicyTest {

    private ReviewSchedulingPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ReviewSchedulingPolicy();
    }

    @Test
    void shouldScheduleNotKnownAfterOneDay() {
        assertActiveResult(ReviewRating.NOT_KNOWN, 0, 1, 0);
    }

    @Test
    void shouldScheduleFuzzyAfterThreeDays() {
        assertActiveResult(ReviewRating.FUZZY, 0, 3, 0);
    }

    @Test
    void shouldScheduleBasicallyMasteredAfterSevenDays() {
        assertActiveResult(ReviewRating.BASICALLY_MASTERED, 0, 7, 0);
    }

    @Test
    void shouldScheduleFirstProficientAfterFourteenDays() {
        assertActiveResult(ReviewRating.PROFICIENT, 0, 14, 1);
    }

    @Test
    void shouldMasterAfterSecondConsecutiveProficientRating() {
        ReviewScheduleResult result = policy.schedule(
                1,
                ReviewRating.PROFICIENT,
                LocalDate.of(2026, 9, 3)
        );

        assertEquals(ReviewStatus.MASTERED, result.reviewStatus());
        assertNull(result.nextReviewDate());
        assertEquals(2, result.consecutiveProficientCount());
    }

    @Test
    void shouldResetProficientCountAfterNonProficientRating() {
        assertActiveResult(ReviewRating.FUZZY, 1, 3, 0);
    }

    @Test
    void shouldCalculateFromActualCompletionDateInsteadOfOldDueDate() {
        LocalDate actualCompletionDate = LocalDate.of(2026, 9, 3);

        ReviewScheduleResult result = policy.schedule(
                0,
                ReviewRating.NOT_KNOWN,
                actualCompletionDate
        );

        assertEquals(
                LocalDate.of(2026, 9, 4),
                result.nextReviewDate()
        );
    }

    @Test
    void shouldCrossMonthBoundary() {
        ReviewScheduleResult result = policy.schedule(
                0,
                ReviewRating.FUZZY,
                LocalDate.of(2026, 9, 30)
        );

        assertEquals(LocalDate.of(2026, 10, 3), result.nextReviewDate());
    }

    @Test
    void shouldCrossYearBoundary() {
        ReviewScheduleResult result = policy.schedule(
                0,
                ReviewRating.BASICALLY_MASTERED,
                LocalDate.of(2026, 12, 30)
        );

        assertEquals(LocalDate.of(2027, 1, 6), result.nextReviewDate());
    }

    @Test
    void shouldHandleLeapDay() {
        ReviewScheduleResult result = policy.schedule(
                0,
                ReviewRating.NOT_KNOWN,
                LocalDate.of(2028, 2, 28)
        );

        assertEquals(LocalDate.of(2028, 2, 29), result.nextReviewDate());
    }

    private void assertActiveResult(
            ReviewRating rating,
            int currentProficientCount,
            int intervalDays,
            int expectedProficientCount
    ) {
        LocalDate completedDate = LocalDate.of(2026, 9, 3);

        ReviewScheduleResult result = policy.schedule(
                currentProficientCount,
                rating,
                completedDate
        );

        assertEquals(ReviewStatus.ACTIVE, result.reviewStatus());
        assertEquals(
                completedDate.plusDays(intervalDays),
                result.nextReviewDate()
        );
        assertEquals(
                expectedProficientCount,
                result.consecutiveProficientCount()
        );
    }
}
