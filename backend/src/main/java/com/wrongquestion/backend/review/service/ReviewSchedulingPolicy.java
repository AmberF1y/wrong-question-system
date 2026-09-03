package com.wrongquestion.backend.review.service;

import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

@Component
public class ReviewSchedulingPolicy {

    static final int NOT_KNOWN_DAYS = 1;
    static final int FUZZY_DAYS = 3;
    static final int BASICALLY_MASTERED_DAYS = 7;
    static final int PROFICIENT_DAYS = 14;
    static final int MASTERED_PROFICIENT_COUNT = 2;

    public ReviewScheduleResult schedule(
            int currentProficientCount,
            ReviewRating rating,
            LocalDate completedDate
    ) {
        Objects.requireNonNull(rating, "rating不能为空");
        Objects.requireNonNull(completedDate, "completedDate不能为空");
        if (currentProficientCount < 0 || currentProficientCount > 1) {
            throw new IllegalArgumentException("ACTIVE状态的连续熟练次数只能是0或1");
        }

        return switch (rating) {
            case NOT_KNOWN -> activeAfter(
                    completedDate,
                    NOT_KNOWN_DAYS,
                    0
            );
            case FUZZY -> activeAfter(
                    completedDate,
                    FUZZY_DAYS,
                    0
            );
            case BASICALLY_MASTERED -> activeAfter(
                    completedDate,
                    BASICALLY_MASTERED_DAYS,
                    0
            );
            case PROFICIENT -> proficientResult(
                    currentProficientCount,
                    completedDate
            );
        };
    }

    private ReviewScheduleResult activeAfter(
            LocalDate completedDate,
            int intervalDays,
            int proficientCount
    ) {
        return new ReviewScheduleResult(
                ReviewStatus.ACTIVE,
                completedDate.plusDays(intervalDays),
                proficientCount
        );
    }

    private ReviewScheduleResult proficientResult(
            int currentProficientCount,
            LocalDate completedDate
    ) {
        int resultingCount = currentProficientCount + 1;
        if (resultingCount == MASTERED_PROFICIENT_COUNT) {
            return new ReviewScheduleResult(
                    ReviewStatus.MASTERED,
                    null,
                    MASTERED_PROFICIENT_COUNT
            );
        }
        return activeAfter(
                completedDate,
                PROFICIENT_DAYS,
                resultingCount
        );
    }
}
