package com.wrongquestion.backend.question.service;

import com.wrongquestion.backend.common.dto.MessageResponse;
import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointNotFoundException;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.dto.CreateQuestionRequest;
import com.wrongquestion.backend.question.dto.QuestionDetailResponse;
import com.wrongquestion.backend.question.dto.QuestionPageResponse;
import com.wrongquestion.backend.question.dto.UpdateQuestionRequest;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.exception.QuestionNotFoundException;
import com.wrongquestion.backend.question.exception.QuestionValidationException;
import com.wrongquestion.backend.question.image.service.QuestionImageTransactionCleanup;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private KnowledgePointRepository knowledgePointRepository;

    @Mock
    private QuestionReviewStateRepository reviewStateRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private QuestionImageTransactionCleanup imageTransactionCleanup;

    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-03T02:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );
        questionService = new QuestionService(
                questionRepository,
                knowledgePointRepository,
                reviewStateRepository,
                entityManager,
                clock,
                imageTransactionCleanup
        );
    }

    @Test
    void shouldCreateQuestionAndDeriveSubjectFromCommonRoot() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        KnowledgePoint network = knowledgePoint(2L, "计算机网络", root);
        KnowledgePoint tcp = knowledgePoint(3L, "TCP", network);
        when(knowledgePointRepository.findAllById(any()))
                .thenReturn(List.of(network, tcp));
        stubSavedQuestion(10L);

        QuestionDetailResponse response = questionService.create(
                request("  题目\n  ", List.of(2L, 3L))
        );

        assertEquals(10L, response.id());
        assertEquals("题目", response.questionText());
        assertEquals("408", response.subject());
        assertEquals(ReviewStatus.ACTIVE, response.reviewStatus());
        assertEquals(LocalDate.of(2026, 9, 4), response.nextReviewDate());
        assertEquals(List.of(2L, 3L), response.knowledgePoints().stream()
                .map(item -> item.id())
                .toList());
        verify(entityManager).refresh(any(Question.class));
    }

    @Test
    void shouldAllowRootAndChildToBeSelectedTogether() {
        KnowledgePoint root = knowledgePoint(1L, "数学", null);
        KnowledgePoint child = knowledgePoint(2L, "极限", root);
        when(knowledgePointRepository.findAllById(any()))
                .thenReturn(List.of(root, child));
        stubSavedQuestion(20L);

        QuestionDetailResponse response = questionService.create(
                request("题目", List.of(1L, 2L))
        );

        assertEquals("数学", response.subject());
        assertEquals(2, response.knowledgePoints().size());
    }

    @Test
    void shouldPreserveInternalWhitespaceAndNewlines() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        when(knowledgePointRepository.findAllById(any()))
                .thenReturn(List.of(root));
        stubSavedQuestion(1L);

        QuestionDetailResponse response = questionService.create(
                request("  第一行\n  第二行  ", List.of(1L))
        );

        assertEquals("第一行\n  第二行", response.questionText());
    }

    @Test
    void shouldRejectBlankText() {
        QuestionValidationException exception = assertThrows(
                QuestionValidationException.class,
                () -> questionService.create(request("   ", List.of(1L)))
        );

        assertEquals("VALIDATION_FAILED", exception.getCode());
        verify(knowledgePointRepository, never()).findAllById(any());
    }

    @Test
    void shouldRejectTextLongerThanLimitAfterStrip() {
        assertThrows(
                QuestionValidationException.class,
                () -> questionService.create(request(
                        "题".repeat(10001),
                        List.of(1L)
                ))
        );
    }

    @Test
    void shouldRejectEmptyKnowledgePointList() {
        QuestionValidationException exception = assertThrows(
                QuestionValidationException.class,
                () -> questionService.create(request("题目", List.of()))
        );

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void shouldRejectNullKnowledgePointId() {
        List<Long> ids = Arrays.asList(1L, null);

        QuestionValidationException exception = assertThrows(
                QuestionValidationException.class,
                () -> questionService.create(request("题目", ids))
        );

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void shouldRejectDuplicateKnowledgePointIds() {
        QuestionValidationException exception = assertThrows(
                QuestionValidationException.class,
                () -> questionService.create(request(
                        "题目",
                        List.of(1L, 1L)
                ))
        );

        assertEquals("QUESTION_DUPLICATE_KNOWLEDGE_POINT", exception.getCode());
        verify(knowledgePointRepository, never()).findAllById(any());
    }

    @Test
    void shouldRejectMissingKnowledgePoint() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        when(knowledgePointRepository.findAllById(any()))
                .thenReturn(List.of(root));

        assertThrows(
                KnowledgePointNotFoundException.class,
                () -> questionService.create(request(
                        "题目",
                        List.of(1L, 99L)
                ))
        );
    }

    @Test
    void shouldRejectKnowledgePointsFromDifferentRoots() {
        KnowledgePoint computer = knowledgePoint(1L, "408", null);
        KnowledgePoint math = knowledgePoint(2L, "数学", null);
        when(knowledgePointRepository.findAllById(any()))
                .thenReturn(List.of(computer, math));

        QuestionValidationException exception = assertThrows(
                QuestionValidationException.class,
                () -> questionService.create(request(
                        "题目",
                        List.of(1L, 2L)
                ))
        );

        assertEquals(
                "QUESTION_KNOWLEDGE_POINTS_CROSS_SUBJECT",
                exception.getCode()
        );
        verify(questionRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReturnDetailAndSortKnowledgePointsById() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        KnowledgePoint first = knowledgePoint(2L, "网络", root);
        KnowledgePoint second = knowledgePoint(3L, "操作系统", root);
        Question question = question(8L, "题目", "408", second, first);
        when(questionRepository.findWithKnowledgePointsById(8L))
                .thenReturn(Optional.of(question));
        when(reviewStateRepository.findById(8L))
                .thenReturn(Optional.of(reviewState(question)));

        QuestionDetailResponse response = questionService.getById(8L);

        assertEquals(List.of(2L, 3L), response.knowledgePoints().stream()
                .map(item -> item.id())
                .toList());
    }

    @Test
    void shouldRejectMissingQuestionDetail() {
        when(questionRepository.findWithKnowledgePointsById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                QuestionNotFoundException.class,
                () -> questionService.getById(99L)
        );
    }

    @Test
    void shouldReturnPageInIdPageOrder() {
        PageRequest pageRequest = PageRequest.of(0, 2);
        when(questionRepository.findPageIds(pageRequest)).thenReturn(
                new PageImpl<>(List.of(3L, 1L), pageRequest, 2)
        );
        Question first = question(1L, "旧题", "408");
        Question third = question(3L, "新题", "数学");
        when(questionRepository.findAllWithKnowledgePointsByIdIn(
                List.of(3L, 1L)
        )).thenReturn(List.of(first, third));
        when(reviewStateRepository.findAllByQuestionIdIn(List.of(3L, 1L)))
                .thenReturn(List.of(
                        reviewState(first),
                        reviewState(third)
                ));

        QuestionPageResponse response = questionService.getPage(0, 2, null);

        assertEquals(List.of(3L, 1L), response.items().stream()
                .map(item -> item.id())
                .toList());
        assertEquals(2, response.totalElements());
        assertEquals(1, response.totalPages());
    }

    @Test
    void shouldTrimAndApplyExactSubjectFilter() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(questionRepository.findPageIdsBySubject("408", pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        QuestionPageResponse response = questionService.getPage(
                0,
                20,
                "  408  "
        );

        assertTrue(response.items().isEmpty());
        verify(questionRepository).findPageIdsBySubject("408", pageRequest);
        verify(questionRepository, never()).findPageIds(any());
    }

    @Test
    void shouldApplyReviewStatusAndCombinedPageFilters() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(questionRepository.findPageIdsByReviewStatus(
                ReviewStatus.MASTERED,
                pageRequest
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));
        when(questionRepository.findPageIdsBySubjectAndReviewStatus(
                "408",
                ReviewStatus.ACTIVE,
                pageRequest
        )).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        questionService.getPage(0, 20, null, ReviewStatus.MASTERED);
        questionService.getPage(0, 20, "  408  ", ReviewStatus.ACTIVE);

        verify(questionRepository).findPageIdsByReviewStatus(
                ReviewStatus.MASTERED,
                pageRequest
        );
        verify(questionRepository).findPageIdsBySubjectAndReviewStatus(
                "408",
                ReviewStatus.ACTIVE,
                pageRequest
        );
    }

    @Test
    void shouldFailPageResponseWhenReviewStateIsMissing() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        Question question = question(1L, "题目", "408");
        when(questionRepository.findPageIds(pageRequest)).thenReturn(
                new PageImpl<>(List.of(1L), pageRequest, 1)
        );
        when(questionRepository.findAllWithKnowledgePointsByIdIn(List.of(1L)))
                .thenReturn(List.of(question));
        when(reviewStateRepository.findAllByQuestionIdIn(List.of(1L)))
                .thenReturn(List.of());

        assertThrows(
                IllegalStateException.class,
                () -> questionService.getPage(0, 20, null)
        );
    }

    @Test
    void shouldReturnEmptyOutOfRangePage() {
        PageRequest pageRequest = PageRequest.of(5, 20);
        when(questionRepository.findPageIds(pageRequest)).thenReturn(
                new PageImpl<>(List.of(), pageRequest, 3)
        );

        QuestionPageResponse response = questionService.getPage(5, 20, null);

        assertTrue(response.items().isEmpty());
        assertEquals(3, response.totalElements());
    }

    @Test
    void shouldRejectInvalidPageParametersAndBlankSubject() {
        assertThrows(
                QuestionValidationException.class,
                () -> questionService.getPage(-1, 20, null)
        );
        assertThrows(
                QuestionValidationException.class,
                () -> questionService.getPage(0, 101, null)
        );
        assertThrows(
                QuestionValidationException.class,
                () -> questionService.getPage(0, 20, "   ")
        );
    }

    @Test
    void shouldReplaceAllEditableFieldsAndSwitchSubject() {
        KnowledgePoint oldRoot = knowledgePoint(1L, "408", null);
        KnowledgePoint oldLeaf = knowledgePoint(2L, "TCP", oldRoot);
        KnowledgePoint newRoot = knowledgePoint(3L, "数学", null);
        KnowledgePoint newLeaf = knowledgePoint(4L, "极限", newRoot);
        Question question = question(10L, "旧题", "408", oldLeaf);
        question.setImagePath("images/original.png");
        when(questionRepository.findWithKnowledgePointsById(10L))
                .thenReturn(Optional.of(question));
        when(knowledgePointRepository.findAllById(any()))
                .thenReturn(List.of(newLeaf));
        when(questionRepository.saveAndFlush(question)).thenReturn(question);
        when(questionRepository.touchUpdatedTime(10L)).thenReturn(1);
        when(reviewStateRepository.findById(10L))
                .thenReturn(Optional.of(reviewState(question)));

        QuestionDetailResponse response = questionService.update(
                10L,
                updateRequest("  新题  ", List.of(4L))
        );

        assertEquals("新题", response.questionText());
        assertEquals("数学", response.subject());
        assertEquals("images/original.png", response.imagePath());
        assertEquals(List.of(4L), response.knowledgePoints().stream()
                .map(item -> item.id())
                .toList());
        verify(questionRepository).touchUpdatedTime(10L);
        verify(entityManager).refresh(question);
    }

    @Test
    void shouldNotSaveUpdateWhenKnowledgePointsCrossSubject() {
        Question question = question(10L, "旧题", "408");
        KnowledgePoint firstRoot = knowledgePoint(1L, "408", null);
        KnowledgePoint secondRoot = knowledgePoint(2L, "数学", null);
        when(questionRepository.findWithKnowledgePointsById(10L))
                .thenReturn(Optional.of(question));
        when(knowledgePointRepository.findAllById(any()))
                .thenReturn(List.of(firstRoot, secondRoot));

        assertThrows(
                QuestionValidationException.class,
                () -> questionService.update(
                        10L,
                        updateRequest("新题", List.of(1L, 2L))
                )
        );

        verify(questionRepository, never()).saveAndFlush(any());
        assertEquals("旧题", question.getQuestionText());
        assertEquals("408", question.getSubject());
    }

    @Test
    void shouldRejectUpdateWhenQuestionDoesNotExist() {
        when(questionRepository.findWithKnowledgePointsById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                QuestionNotFoundException.class,
                () -> questionService.update(
                        99L,
                        updateRequest("题目", List.of(1L))
                )
        );

        verify(knowledgePointRepository, never()).findAllById(any());
    }

    @Test
    void shouldDeleteQuestionAndReturnMessage() {
        Question question = question(10L, "题目", "408");
        question.setImagePath(
                "questions/10/00000000-0000-0000-0000-000000000010.png"
        );
        when(questionRepository.findById(10L)).thenReturn(Optional.of(question));

        MessageResponse response = questionService.delete(10L);

        assertEquals("错题删除成功", response.message());
        verify(imageTransactionCleanup).registerDeleteAfterCommit(
                "questions/10/00000000-0000-0000-0000-000000000010.png"
        );
        verify(questionRepository).delete(question);
        verify(questionRepository).flush();
    }

    @Test
    void shouldRejectDeleteWhenQuestionDoesNotExist() {
        when(questionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                QuestionNotFoundException.class,
                () -> questionService.delete(99L)
        );

        verify(questionRepository, never()).delete(any());
    }

    private CreateQuestionRequest request(
            String questionText,
            List<Long> knowledgePointIds
    ) {
        return new CreateQuestionRequest(
                questionText,
                "  未作答  ",
                "  正确答案  ",
                "  解析  ",
                "  概念错误  ",
                knowledgePointIds
        );
    }

    private UpdateQuestionRequest updateRequest(
            String questionText,
            List<Long> knowledgePointIds
    ) {
        return new UpdateQuestionRequest(
                questionText,
                "新错误答案",
                "新正确答案",
                "新解析",
                "新错误原因",
                knowledgePointIds
        );
    }

    private KnowledgePoint knowledgePoint(
            Long id,
            String name,
            KnowledgePoint parent
    ) {
        KnowledgePoint knowledgePoint = new KnowledgePoint(name, parent);
        ReflectionTestUtils.setField(knowledgePoint, "id", id);
        return knowledgePoint;
    }

    private Question question(
            Long id,
            String questionText,
            String subject,
            KnowledgePoint... knowledgePoints
    ) {
        Question question = new Question(
                questionText,
                "错误答案",
                "正确答案",
                "解析",
                "错误原因",
                subject
        );
        ReflectionTestUtils.setField(question, "id", id);
        ReflectionTestUtils.setField(
                question,
                "createdTime",
                LocalDateTime.of(2026, 9, 2, 10, 0)
        );
        ReflectionTestUtils.setField(
                question,
                "updatedTime",
                LocalDateTime.of(2026, 9, 2, 10, 0)
        );
        Arrays.stream(knowledgePoints).forEach(question::addKnowledgePoint);
        return question;
    }

    private void stubSavedQuestion(Long id) {
        when(questionRepository.saveAndFlush(any(Question.class)))
                .thenAnswer(invocation -> {
                    Question question = invocation.getArgument(0);
                    ReflectionTestUtils.setField(question, "id", id);
                    return question;
                });
        when(reviewStateRepository.saveAndFlush(any(QuestionReviewState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private QuestionReviewState reviewState(Question question) {
        QuestionReviewState state = new QuestionReviewState(
                question,
                LocalDate.of(2026, 9, 4)
        );
        ReflectionTestUtils.setField(state, "questionId", question.getId());
        return state;
    }
}
