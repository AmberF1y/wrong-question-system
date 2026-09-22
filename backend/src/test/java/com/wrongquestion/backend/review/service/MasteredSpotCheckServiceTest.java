package com.wrongquestion.backend.review.service;

import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.dto.MasteredSpotCheckResponse;
import com.wrongquestion.backend.review.dto.ReviewActionResponse;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewEventType;
import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewRecord;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import com.wrongquestion.backend.review.exception.ReviewConflictException;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;
import com.wrongquestion.backend.review.repository.ReviewRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasteredSpotCheckServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T02:20:30Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    @Mock
    private QuestionReviewStateRepository reviewStateRepository;

    @Mock
    private ReviewRecordRepository reviewRecordRepository;

    @Mock
    private QuestionRepository questionRepository;

    private MasteredSpotCheckService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));
        service = new MasteredSpotCheckService(
                reviewStateRepository,
                reviewRecordRepository,
                questionRepository,
                new ReviewSchedulingPolicy(),
                clock
        );
    }

    @Test
    void shouldSelectOneStableCandidateForTheDay() {
        QuestionReviewState state = masteredState(42L, "抽查题", "数学");
        LocalDate cooldownStart = TODAY.minusDays(29);
        when(reviewStateRepository.countSpotCheckCandidates(
                ReviewStatus.MASTERED,
                ReviewEventType.SPOT_CHECK,
                cooldownStart,
                null
        )).thenReturn(3L);
        when(reviewStateRepository.findSpotCheckCandidates(
                eq(ReviewStatus.MASTERED),
                eq(ReviewEventType.SPOT_CHECK),
                eq(cooldownStart),
                isNull(),
                any(Pageable.class)
        )).thenReturn(List.of(state));

        MasteredSpotCheckResponse first = service.getTodaySpotCheck(null);
        MasteredSpotCheckResponse second = service.getTodaySpotCheck(null);

        assertEquals(first, second);
        assertEquals(3, first.eligibleCount());
        assertEquals(30, first.cooldownDays());
        assertEquals(42L, first.question().id());
        assertEquals("抽查题", first.question().questionText());
        assertEquals("数学", first.question().subject());

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(reviewStateRepository, org.mockito.Mockito.times(2))
                .findSpotCheckCandidates(
                        eq(ReviewStatus.MASTERED),
                        eq(ReviewEventType.SPOT_CHECK),
                        eq(cooldownStart),
                        isNull(),
                        pageableCaptor.capture()
                );
        assertEquals(
                pageableCaptor.getAllValues().getFirst(),
                pageableCaptor.getAllValues().getLast()
        );
        assertTrue(pageableCaptor.getValue().getPageNumber() < 3);
        assertEquals(1, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void shouldReturnCompletedWithoutSelectingAnotherQuestion() {
        when(reviewRecordRepository.existsByEventTypeAndBusinessDate(
                ReviewEventType.SPOT_CHECK,
                TODAY
        )).thenReturn(true);

        MasteredSpotCheckResponse response = service.getTodaySpotCheck(null);

        assertTrue(response.completedToday());
        assertEquals(0, response.eligibleCount());
        assertNull(response.question());
        verify(reviewStateRepository, never()).countSpotCheckCandidates(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void shouldRequireDueReviewsToBeCompletedFirst() {
        when(reviewStateRepository
                .countByReviewStatusAndNextReviewDateLessThanEqual(
                        ReviewStatus.ACTIVE,
                        TODAY
                )).thenReturn(1L);

        ReviewConflictException exception = assertThrows(
                ReviewConflictException.class,
                () -> service.getTodaySpotCheck(null)
        );

        assertEquals("SPOT_CHECK_DUE_REVIEW_REMAINING", exception.getCode());
        verify(reviewRecordRepository, never())
                .existsByEventTypeAndBusinessDate(any(), any());
    }

    @Test
    void shouldKeepQuestionMasteredAfterProficientSpotCheck() {
        QuestionReviewState state = masteredState(42L, "抽查题", "408");
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));
        when(reviewRecordRepository.saveAndFlush(any(ReviewRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewActionResponse response = service.evaluateSpotCheck(
                42L,
                ReviewRating.PROFICIENT,
                null
        );

        assertEquals(ReviewEventType.SPOT_CHECK, response.eventType());
        assertEquals(ReviewStatus.MASTERED, response.reviewStatus());
        assertNull(response.nextReviewDate());
        assertEquals(2, response.consecutiveProficientCount());
        assertEquals(NOW, response.lastReviewedAt());

        ArgumentCaptor<ReviewRecord> recordCaptor =
                ArgumentCaptor.forClass(ReviewRecord.class);
        verify(reviewRecordRepository).saveAndFlush(recordCaptor.capture());
        ReviewRecord record = recordCaptor.getValue();
        assertEquals(ReviewEventType.SPOT_CHECK, record.getEventType());
        assertEquals(ReviewRating.PROFICIENT, record.getRating());
        assertNull(record.getScheduledReviewDate());
        assertEquals(ReviewStatus.MASTERED, record.getResultingStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "NOT_KNOWN,1",
            "FUZZY,3",
            "BASICALLY_MASTERED,7"
    })
    void shouldReturnFailedSpotCheckToNormalQueue(
            ReviewRating rating,
            int intervalDays
    ) {
        QuestionReviewState state = masteredState(42L, "抽查题", "408");
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));
        when(reviewRecordRepository.saveAndFlush(any(ReviewRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewActionResponse response = service.evaluateSpotCheck(
                42L,
                rating,
                null
        );

        assertEquals(ReviewStatus.ACTIVE, response.reviewStatus());
        assertEquals(TODAY.plusDays(intervalDays), response.nextReviewDate());
        assertEquals(0, response.consecutiveProficientCount());
    }

    @Test
    void shouldRejectQuestionStillInsideCooldown() {
        QuestionReviewState state = masteredState(42L, "抽查题", "408");
        when(reviewStateRepository.findById(42L)).thenReturn(Optional.of(state));
        when(reviewRecordRepository
                .existsByQuestion_IdAndEventTypeAndBusinessDateGreaterThanEqual(
                        42L,
                        ReviewEventType.SPOT_CHECK,
                        TODAY.minusDays(29)
                )).thenReturn(true);

        ReviewConflictException exception = assertThrows(
                ReviewConflictException.class,
                () -> service.evaluateSpotCheck(
                        42L,
                        ReviewRating.PROFICIENT,
                        null
                )
        );

        assertEquals("SPOT_CHECK_COOLDOWN", exception.getCode());
        verify(reviewStateRepository, never()).flush();
        verify(reviewRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldUseTrimmedSubjectForDueCheckAndCandidateSelection() {
        QuestionReviewState state = masteredState(42L, "抽查题", "数学");
        when(reviewStateRepository.countDueBySubject(
                ReviewStatus.ACTIVE,
                TODAY,
                "数学"
        )).thenReturn(0L);
        when(reviewStateRepository.countSpotCheckCandidates(
                ReviewStatus.MASTERED,
                ReviewEventType.SPOT_CHECK,
                TODAY.minusDays(29),
                "数学"
        )).thenReturn(1L);
        when(reviewStateRepository.findSpotCheckCandidates(
                eq(ReviewStatus.MASTERED),
                eq(ReviewEventType.SPOT_CHECK),
                eq(TODAY.minusDays(29)),
                eq("数学"),
                any(Pageable.class)
        )).thenReturn(List.of(state));

        MasteredSpotCheckResponse response =
                service.getTodaySpotCheck("  数学  ");

        assertEquals("数学", response.question().subject());
    }

    private QuestionReviewState masteredState(
            Long id,
            String text,
            String subject
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
        QuestionReviewState state = new QuestionReviewState(question, TODAY);
        ReflectionTestUtils.setField(state, "questionId", id);
        state.applyEvaluation(
                ReviewStatus.MASTERED,
                null,
                2,
                NOW.minusSeconds(3600)
        );
        return state;
    }
}
