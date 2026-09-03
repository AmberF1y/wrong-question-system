package com.wrongquestion.backend.review.service;

import com.wrongquestion.backend.question.exception.QuestionNotFoundException;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.dto.DueQuestionResponse;
import com.wrongquestion.backend.review.dto.DueReviewResponse;
import com.wrongquestion.backend.review.dto.ReviewActionResponse;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewEventType;
import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewRecord;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import com.wrongquestion.backend.review.exception.ReviewConflictException;
import com.wrongquestion.backend.review.exception.ReviewValidationException;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;
import com.wrongquestion.backend.review.repository.ReviewRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReviewService {

    private final QuestionReviewStateRepository reviewStateRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final QuestionRepository questionRepository;
    private final ReviewSchedulingPolicy schedulingPolicy;
    private final Clock clock;

    public ReviewService(
            QuestionReviewStateRepository reviewStateRepository,
            ReviewRecordRepository reviewRecordRepository,
            QuestionRepository questionRepository,
            ReviewSchedulingPolicy schedulingPolicy,
            Clock clock
    ) {
        this.reviewStateRepository = reviewStateRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.questionRepository = questionRepository;
        this.schedulingPolicy = schedulingPolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DueReviewResponse getNextDue(String subject) {
        String normalizedSubject = normalizeSubject(subject);
        LocalDate today = LocalDate.now(clock);

        long dueCount = normalizedSubject == null
                ? reviewStateRepository
                        .countByReviewStatusAndNextReviewDateLessThanEqual(
                                ReviewStatus.ACTIVE,
                                today
                        )
                : reviewStateRepository.countDueBySubject(
                        ReviewStatus.ACTIVE,
                        today,
                        normalizedSubject
                );

        List<QuestionReviewState> states = normalizedSubject == null
                ? reviewStateRepository.findDue(
                        ReviewStatus.ACTIVE,
                        today,
                        PageRequest.of(0, 1)
                )
                : reviewStateRepository.findDueBySubject(
                        ReviewStatus.ACTIVE,
                        today,
                        normalizedSubject,
                        PageRequest.of(0, 1)
                );

        if (states.isEmpty()) {
            if (dueCount != 0) {
                throw new IllegalStateException("待复习数量与队列数据不一致");
            }
            return new DueReviewResponse(0, null);
        }

        QuestionReviewState state = states.getFirst();
        return new DueReviewResponse(
                dueCount,
                new DueQuestionResponse(
                        state.getQuestionId(),
                        state.getQuestion().getQuestionText(),
                        state.getQuestion().getImagePath(),
                        state.getQuestion().getSubject(),
                        state.getNextReviewDate()
                )
        );
    }

    @Transactional
    public ReviewActionResponse evaluate(
            Long questionId,
            ReviewRating rating
    ) {
        QuestionReviewState state = findState(questionId);
        Instant occurredAt = currentInstant();
        LocalDate today = businessDate(occurredAt);

        if (state.getReviewStatus() == ReviewStatus.MASTERED) {
            throw conflict(
                    "REVIEW_ALREADY_MASTERED",
                    "已掌握错题不能提交普通复习评价"
            );
        }
        if (state.getNextReviewDate().isAfter(today)) {
            throw conflict("REVIEW_NOT_DUE", "错题尚未到复习日期");
        }

        LocalDate scheduledReviewDate = state.getNextReviewDate();
        ReviewScheduleResult result = schedulingPolicy.schedule(
                state.getConsecutiveProficientCount(),
                rating,
                today
        );

        state.applyEvaluation(
                result.reviewStatus(),
                result.nextReviewDate(),
                result.consecutiveProficientCount(),
                occurredAt
        );
        reviewStateRepository.flush();

        ReviewRecord record = ReviewRecord.evaluation(
                state.getQuestion(),
                rating,
                today,
                occurredAt,
                scheduledReviewDate,
                result.reviewStatus(),
                result.nextReviewDate(),
                result.consecutiveProficientCount()
        );
        reviewRecordRepository.saveAndFlush(record);

        return toActionResponse(
                state,
                ReviewEventType.EVALUATION,
                rating,
                occurredAt
        );
    }

    @Transactional
    public ReviewActionResponse reactivate(Long questionId) {
        QuestionReviewState state = findState(questionId);
        if (state.getReviewStatus() != ReviewStatus.MASTERED) {
            throw conflict(
                    "REVIEW_NOT_MASTERED",
                    "只有已掌握错题可以重新加入复习"
            );
        }

        Instant occurredAt = currentInstant();
        LocalDate today = businessDate(occurredAt);
        state.reactivate(today);
        reviewStateRepository.flush();

        ReviewRecord record = ReviewRecord.reactivation(
                state.getQuestion(),
                today,
                occurredAt,
                state.getReviewStatus(),
                state.getNextReviewDate(),
                state.getConsecutiveProficientCount()
        );
        reviewRecordRepository.saveAndFlush(record);

        return toActionResponse(
                state,
                ReviewEventType.REACTIVATION,
                null,
                occurredAt
        );
    }

    private QuestionReviewState findState(Long questionId) {
        return reviewStateRepository.findById(questionId)
                .orElseThrow(() -> {
                    if (!questionRepository.existsById(questionId)) {
                        return new QuestionNotFoundException("错题不存在");
                    }
                    return new IllegalStateException("错题缺少复习状态：" + questionId);
                });
    }

    private String normalizeSubject(String subject) {
        if (subject == null) {
            return null;
        }
        String normalized = subject.strip();
        if (normalized.isBlank()) {
            throw new ReviewValidationException("subject不能为空白");
        }
        return normalized;
    }

    private LocalDate businessDate(Instant occurredAt) {
        return occurredAt.atZone(clock.getZone()).toLocalDate();
    }

    private Instant currentInstant() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private ReviewActionResponse toActionResponse(
            QuestionReviewState state,
            ReviewEventType eventType,
            ReviewRating rating,
            Instant occurredAt
    ) {
        return new ReviewActionResponse(
                state.getQuestionId(),
                eventType,
                rating,
                occurredAt,
                state.getReviewStatus(),
                state.getNextReviewDate(),
                state.getConsecutiveProficientCount(),
                state.getLastReviewedAt()
        );
    }

    private ReviewConflictException conflict(String code, String message) {
        return new ReviewConflictException(code, message);
    }
}
