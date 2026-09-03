package com.wrongquestion.backend.review.service;

import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.exception.QuestionNotFoundException;
import com.wrongquestion.backend.question.repository.QuestionRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-03T02:20:30Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    @Mock
    private QuestionReviewStateRepository reviewStateRepository;

    @Mock
    private ReviewRecordRepository reviewRecordRepository;

    @Mock
    private QuestionRepository questionRepository;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));
        reviewService = new ReviewService(
                reviewStateRepository,
                reviewRecordRepository,
                questionRepository,
                new ReviewSchedulingPolicy(),
                clock
        );
    }

    @Test
    void shouldReturnDueCountIncludingCurrentQuestion() {
        QuestionReviewState state = state(42L, "题目", "408", TODAY.minusDays(2));
        when(reviewStateRepository
                .countByReviewStatusAndNextReviewDateLessThanEqual(
                        ReviewStatus.ACTIVE,
                        TODAY
                )).thenReturn(3L);
        when(reviewStateRepository.findDue(
                ReviewStatus.ACTIVE,
                TODAY,
                PageRequest.of(0, 1)
        )).thenReturn(List.of(state));

        DueReviewResponse response = reviewService.getNextDue(null);

        assertEquals(3, response.dueCount());
        assertEquals(42L, response.question().id());
        assertEquals("题目", response.question().questionText());
        assertEquals("408", response.question().subject());
        assertEquals(TODAY.minusDays(2), response.question().nextReviewDate());
    }

    @Test
    void shouldTrimSubjectAndUseSubjectQueries() {
        QuestionReviewState state = state(1L, "题目", "数学", TODAY);
        when(reviewStateRepository.countDueBySubject(
                ReviewStatus.ACTIVE,
                TODAY,
                "数学"
        )).thenReturn(1L);
        when(reviewStateRepository.findDueBySubject(
                ReviewStatus.ACTIVE,
                TODAY,
                "数学",
                PageRequest.of(0, 1)
        )).thenReturn(List.of(state));

        DueReviewResponse response = reviewService.getNextDue("  数学  ");

        assertEquals(1, response.dueCount());
        assertEquals("数学", response.question().subject());
    }

    @Test
    void shouldReturnNormalEmptyQueueResponse() {
        when(reviewStateRepository
                .countByReviewStatusAndNextReviewDateLessThanEqual(
                        ReviewStatus.ACTIVE,
                        TODAY
                )).thenReturn(0L);
        when(reviewStateRepository.findDue(
                ReviewStatus.ACTIVE,
                TODAY,
                PageRequest.of(0, 1)
        )).thenReturn(List.of());

        DueReviewResponse response = reviewService.getNextDue(null);

        assertEquals(0, response.dueCount());
        assertNull(response.question());
    }

    @Test
    void shouldRejectBlankSubject() {
        assertThrows(
                ReviewValidationException.class,
                () -> reviewService.getNextDue("   ")
        );
        verify(reviewStateRepository, never())
                .countByReviewStatusAndNextReviewDateLessThanEqual(
                        any(),
                        any()
                );
    }

    @Test
    void shouldEvaluateDueQuestionAndWriteCompleteHistory() {
        QuestionReviewState state = state(42L, "题目", "408", TODAY.minusDays(1));
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));
        when(reviewRecordRepository.saveAndFlush(any(ReviewRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewActionResponse response = reviewService.evaluate(
                42L,
                ReviewRating.BASICALLY_MASTERED
        );

        assertEquals(ReviewEventType.EVALUATION, response.eventType());
        assertEquals(ReviewRating.BASICALLY_MASTERED, response.rating());
        assertEquals(ReviewStatus.ACTIVE, response.reviewStatus());
        assertEquals(TODAY.plusDays(7), response.nextReviewDate());
        assertEquals(0, response.consecutiveProficientCount());
        assertEquals(NOW, response.lastReviewedAt());

        ArgumentCaptor<ReviewRecord> captor =
                ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordRepository).saveAndFlush(captor.capture());
        ReviewRecord record = captor.getValue();
        assertEquals(ReviewEventType.EVALUATION, record.getEventType());
        assertEquals(ReviewRating.BASICALLY_MASTERED, record.getRating());
        assertEquals(TODAY, record.getBusinessDate());
        assertEquals(NOW, record.getOccurredAt());
        assertEquals(TODAY.minusDays(1), record.getScheduledReviewDate());
        assertEquals(TODAY.plusDays(7), record.getResultingNextReviewDate());
    }

    @Test
    void shouldMasterQuestionAfterSecondProficientRating() {
        QuestionReviewState state = state(42L, "题目", "408", TODAY);
        state.applyEvaluation(
                ReviewStatus.ACTIVE,
                TODAY,
                1,
                NOW.minusSeconds(3600)
        );
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));
        when(reviewRecordRepository.saveAndFlush(any(ReviewRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewActionResponse response = reviewService.evaluate(
                42L,
                ReviewRating.PROFICIENT
        );

        assertEquals(ReviewStatus.MASTERED, response.reviewStatus());
        assertNull(response.nextReviewDate());
        assertEquals(2, response.consecutiveProficientCount());
    }

    @Test
    void shouldRejectEvaluationBeforeDueDateWithoutHistory() {
        QuestionReviewState state = state(42L, "题目", "408", TODAY.plusDays(1));
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));

        ReviewConflictException exception = assertThrows(
                ReviewConflictException.class,
                () -> reviewService.evaluate(42L, ReviewRating.NOT_KNOWN)
        );

        assertEquals("REVIEW_NOT_DUE", exception.getCode());
        verify(reviewRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectEvaluationForMasteredQuestionWithoutHistory() {
        QuestionReviewState state = masteredState(42L);
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));

        ReviewConflictException exception = assertThrows(
                ReviewConflictException.class,
                () -> reviewService.evaluate(42L, ReviewRating.PROFICIENT)
        );

        assertEquals("REVIEW_ALREADY_MASTERED", exception.getCode());
        verify(reviewRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReactivateMasteredQuestionAndPreserveLastReviewedAt() {
        QuestionReviewState state = masteredState(42L);
        Instant lastReviewedAt = state.getLastReviewedAt();
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));
        when(reviewRecordRepository.saveAndFlush(any(ReviewRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewActionResponse response = reviewService.reactivate(42L);

        assertEquals(ReviewEventType.REACTIVATION, response.eventType());
        assertNull(response.rating());
        assertEquals(ReviewStatus.ACTIVE, response.reviewStatus());
        assertEquals(TODAY, response.nextReviewDate());
        assertEquals(0, response.consecutiveProficientCount());
        assertEquals(lastReviewedAt, response.lastReviewedAt());

        ArgumentCaptor<ReviewRecord> captor =
                ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordRepository).saveAndFlush(captor.capture());
        assertEquals(
                ReviewEventType.REACTIVATION,
                captor.getValue().getEventType()
        );
        assertNull(captor.getValue().getRating());
        assertNull(captor.getValue().getScheduledReviewDate());
    }

    @Test
    void shouldRejectReactivationForActiveQuestion() {
        QuestionReviewState state = state(42L, "题目", "408", TODAY);
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));

        ReviewConflictException exception = assertThrows(
                ReviewConflictException.class,
                () -> reviewService.reactivate(42L)
        );

        assertEquals("REVIEW_NOT_MASTERED", exception.getCode());
        verify(reviewRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReturnQuestionNotFoundWhenQuestionAndStateAreMissing() {
        when(reviewStateRepository.findById(99L)).thenReturn(Optional.empty());
        when(questionRepository.existsById(99L)).thenReturn(false);

        assertThrows(
                QuestionNotFoundException.class,
                () -> reviewService.evaluate(99L, ReviewRating.NOT_KNOWN)
        );
    }

    @Test
    void shouldFailWhenExistingQuestionHasNoReviewState() {
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.empty());
        when(questionRepository.existsById(42L)).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reviewService.evaluate(42L, ReviewRating.NOT_KNOWN)
        );

        assertTrue(exception.getMessage().contains("缺少复习状态"));
    }

    private QuestionReviewState state(
            Long id,
            String text,
            String subject,
            LocalDate nextReviewDate
    ) {
        Question question = new Question(
                text,
                "错误答案",
                "正确答案",
                "解析",
                "错误原因",
                subject
        );
        ReflectionTestUtils.setField(question, "id", id);
        QuestionReviewState state = new QuestionReviewState(
                question,
                nextReviewDate
        );
        ReflectionTestUtils.setField(state, "questionId", id);
        return state;
    }

    private QuestionReviewState masteredState(Long id) {
        QuestionReviewState state = state(id, "题目", "408", TODAY);
        state.applyEvaluation(
                ReviewStatus.MASTERED,
                null,
                2,
                NOW.minusSeconds(3600)
        );
        return state;
    }
}
