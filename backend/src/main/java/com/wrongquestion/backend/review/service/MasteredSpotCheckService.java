package com.wrongquestion.backend.review.service;

import com.wrongquestion.backend.question.exception.QuestionNotFoundException;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.dto.MasteredSpotCheckResponse;
import com.wrongquestion.backend.review.dto.ReviewActionResponse;
import com.wrongquestion.backend.review.dto.SpotCheckQuestionResponse;
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
public class MasteredSpotCheckService {

    public static final int COOLDOWN_DAYS = 30;

    private final QuestionReviewStateRepository reviewStateRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final QuestionRepository questionRepository;
    private final ReviewSchedulingPolicy schedulingPolicy;
    private final Clock clock;

    public MasteredSpotCheckService(
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
    public MasteredSpotCheckResponse getTodaySpotCheck(String subject) {
        String normalizedSubject = normalizeSubject(subject);
        LocalDate today = LocalDate.now(clock);
        ensureDueQueueEmpty(normalizedSubject, today);

        if (reviewRecordRepository.existsByEventTypeAndBusinessDate(
                ReviewEventType.SPOT_CHECK,
                today
        )) {
            return response(true, 0, null);
        }

        LocalDate earliestBusinessDate = cooldownStart(today);
        long eligibleCount = reviewStateRepository.countSpotCheckCandidates(
                ReviewStatus.MASTERED,
                ReviewEventType.SPOT_CHECK,
                earliestBusinessDate,
                normalizedSubject
        );
        if (eligibleCount == 0) {
            return response(false, 0, null);
        }
        if (eligibleCount > Integer.MAX_VALUE) {
            throw new IllegalStateException("已掌握题数量超出抽查范围");
        }

        int candidateIndex = stableCandidateIndex(
                today,
                normalizedSubject,
                eligibleCount
        );
        List<QuestionReviewState> candidates =
                reviewStateRepository.findSpotCheckCandidates(
                        ReviewStatus.MASTERED,
                        ReviewEventType.SPOT_CHECK,
                        earliestBusinessDate,
                        normalizedSubject,
                        PageRequest.of(candidateIndex, 1)
                );
        if (candidates.size() != 1) {
            throw new IllegalStateException("抽查候选数量与候选题不一致");
        }

        QuestionReviewState state = candidates.getFirst();
        return response(
                false,
                eligibleCount,
                new SpotCheckQuestionResponse(
                        state.getQuestionId(),
                        state.getQuestion().getQuestionText(),
                        state.getQuestion().getImagePath(),
                        state.getQuestion().getSubject()
                )
        );
    }

    @Transactional
    public ReviewActionResponse evaluateSpotCheck(
            Long questionId,
            ReviewRating rating,
            String subject
    ) {
        String normalizedSubject = normalizeSubject(subject);
        QuestionReviewState state = findState(questionId);
        Instant occurredAt = currentInstant();
        LocalDate today = businessDate(occurredAt);

        ensureDueQueueEmpty(normalizedSubject, today);
        if (reviewRecordRepository.existsByEventTypeAndBusinessDate(
                ReviewEventType.SPOT_CHECK,
                today
        )) {
            throw conflict(
                    "SPOT_CHECK_ALREADY_COMPLETED",
                    "今日已完成已掌握题抽查"
            );
        }
        if (state.getReviewStatus() != ReviewStatus.MASTERED) {
            throw conflict(
                    "SPOT_CHECK_NOT_MASTERED",
                    "只有已掌握错题可以提交抽查评价"
            );
        }
        if (normalizedSubject != null
                && !normalizedSubject.equals(state.getQuestion().getSubject())) {
            throw conflict(
                    "SPOT_CHECK_SUBJECT_MISMATCH",
                    "抽查题目不属于当前复习科目"
            );
        }
        if (reviewRecordRepository
                .existsByQuestion_IdAndEventTypeAndBusinessDateGreaterThanEqual(
                        questionId,
                        ReviewEventType.SPOT_CHECK,
                        cooldownStart(today)
                )) {
            throw conflict(
                    "SPOT_CHECK_COOLDOWN",
                    "该错题仍在抽查冷却期内"
            );
        }

        ReviewScheduleResult result = spotCheckResult(rating, today);
        state.applyEvaluation(
                result.reviewStatus(),
                result.nextReviewDate(),
                result.consecutiveProficientCount(),
                occurredAt
        );
        reviewStateRepository.flush();

        ReviewRecord record = ReviewRecord.spotCheck(
                state.getQuestion(),
                rating,
                today,
                occurredAt,
                result.reviewStatus(),
                result.nextReviewDate(),
                result.consecutiveProficientCount()
        );
        reviewRecordRepository.saveAndFlush(record);

        return new ReviewActionResponse(
                state.getQuestionId(),
                ReviewEventType.SPOT_CHECK,
                rating,
                occurredAt,
                state.getReviewStatus(),
                state.getNextReviewDate(),
                state.getConsecutiveProficientCount(),
                state.getLastReviewedAt()
        );
    }

    private ReviewScheduleResult spotCheckResult(
            ReviewRating rating,
            LocalDate today
    ) {
        if (rating == ReviewRating.PROFICIENT) {
            return new ReviewScheduleResult(
                    ReviewStatus.MASTERED,
                    null,
                    ReviewSchedulingPolicy.MASTERED_PROFICIENT_COUNT
            );
        }
        return schedulingPolicy.schedule(0, rating, today);
    }

    private void ensureDueQueueEmpty(String subject, LocalDate today) {
        long dueCount = subject == null
                ? reviewStateRepository
                        .countByReviewStatusAndNextReviewDateLessThanEqual(
                                ReviewStatus.ACTIVE,
                                today
                        )
                : reviewStateRepository.countDueBySubject(
                        ReviewStatus.ACTIVE,
                        today,
                        subject
                );
        if (dueCount > 0) {
            throw conflict(
                    "SPOT_CHECK_DUE_REVIEW_REMAINING",
                    "请先完成当前范围内的到期复习"
            );
        }
    }

    private QuestionReviewState findState(Long questionId) {
        return reviewStateRepository.findById(questionId)
                .orElseThrow(() -> {
                    if (!questionRepository.existsById(questionId)) {
                        return new QuestionNotFoundException("错题不存在");
                    }
                    return new IllegalStateException(
                            "错题缺少复习状态：" + questionId
                    );
                });
    }

    private MasteredSpotCheckResponse response(
            boolean completedToday,
            long eligibleCount,
            SpotCheckQuestionResponse question
    ) {
        return new MasteredSpotCheckResponse(
                completedToday,
                eligibleCount,
                COOLDOWN_DAYS,
                question
        );
    }

    private LocalDate cooldownStart(LocalDate today) {
        return today.minusDays(COOLDOWN_DAYS - 1L);
    }

    private int stableCandidateIndex(
            LocalDate today,
            String subject,
            long eligibleCount
    ) {
        long value = today.toEpochDay() ^ 0x9E3779B97F4A7C15L;
        if (subject != null) {
            value ^= Integer.toUnsignedLong(subject.hashCode())
                    * 0xBF58476D1CE4E5B9L;
        }
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (int) Math.floorMod(value, eligibleCount);
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

    private ReviewConflictException conflict(String code, String message) {
        return new ReviewConflictException(code, message);
    }
}
