package com.wrongquestion.backend.review.repository;

import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewRecord;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ReviewRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);
    private static final Instant NOW = Instant.parse("2026-09-03T02:00:00Z");

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionReviewStateRepository reviewStateRepository;

    @Autowired
    private ReviewRecordRepository reviewRecordRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistSharedPrimaryKeyAndEnumValues() {
        Question question = saveQuestion("共享主键题", "408");
        QuestionReviewState state = reviewStateRepository.saveAndFlush(
                new QuestionReviewState(question, TODAY)
        );
        ReviewRecord record = reviewRecordRepository.saveAndFlush(
                ReviewRecord.evaluation(
                        question,
                        ReviewRating.FUZZY,
                        TODAY,
                        NOW,
                        TODAY,
                        ReviewStatus.ACTIVE,
                        TODAY.plusDays(3),
                        0
                )
        );

        assertEquals(question.getId(), state.getQuestionId());
        assertNotNull(record.getId());

        entityManager.clear();

        QuestionReviewState loadedState = reviewStateRepository
                .findById(question.getId())
                .orElseThrow();
        ReviewRecord loadedRecord = reviewRecordRepository
                .findById(record.getId())
                .orElseThrow();
        assertEquals(ReviewStatus.ACTIVE, loadedState.getReviewStatus());
        assertEquals(ReviewRating.FUZZY, loadedRecord.getRating());
    }

    @Test
    void shouldSelectOverdueAndDueQuestionsInRequiredOrder() {
        Question first = saveQuestion("最早到期", "408");
        Question second = saveQuestion("同日较小ID", "408");
        Question third = saveQuestion("同日较大ID", "数学");
        Question future = saveQuestion("未来题", "408");
        Question mastered = saveQuestion("已掌握题", "408");

        saveActiveState(second, TODAY);
        saveActiveState(third, TODAY);
        saveActiveState(first, TODAY.minusDays(2));
        saveActiveState(future, TODAY.plusDays(1));
        saveMasteredState(mastered);

        assertEquals(
                3,
                reviewStateRepository
                        .countByReviewStatusAndNextReviewDateLessThanEqual(
                                ReviewStatus.ACTIVE,
                                TODAY
                        )
        );

        List<QuestionReviewState> due = reviewStateRepository.findDue(
                ReviewStatus.ACTIVE,
                TODAY,
                PageRequest.of(0, 10)
        );

        assertEquals(
                List.of(first.getId(), second.getId(), third.getId()),
                due.stream().map(QuestionReviewState::getQuestionId).toList()
        );
        assertEquals(
                2,
                reviewStateRepository.countDueBySubject(
                        ReviewStatus.ACTIVE,
                        TODAY,
                        "408"
                )
        );
        assertEquals(
                List.of(first.getId(), second.getId()),
                reviewStateRepository.findDueBySubject(
                                ReviewStatus.ACTIVE,
                                TODAY,
                                "408",
                                PageRequest.of(0, 10)
                        ).stream()
                        .map(QuestionReviewState::getQuestionId)
                        .toList()
        );
    }

    @Test
    void shouldFilterQuestionPageByStatusAndSubject() {
        Question active408 = saveQuestion("408活动题", "408");
        Question mastered408 = saveQuestion("408掌握题", "408");
        Question masteredMath = saveQuestion("数学掌握题", "数学");
        saveActiveState(active408, TODAY);
        saveMasteredState(mastered408);
        saveMasteredState(masteredMath);

        assertEquals(
                List.of(masteredMath.getId(), mastered408.getId()),
                questionRepository.findPageIdsByReviewStatus(
                                ReviewStatus.MASTERED,
                                PageRequest.of(0, 20)
                        ).getContent()
        );
        assertEquals(
                List.of(mastered408.getId()),
                questionRepository.findPageIdsBySubjectAndReviewStatus(
                                "408",
                                ReviewStatus.MASTERED,
                                PageRequest.of(0, 20)
                        ).getContent()
        );
    }

    @Test
    void shouldRejectInvalidStateCombinationAtDatabaseBoundary() {
        Question question = saveQuestion("非法状态题", "408");

        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO question_review_state (
                            question_id,
                            review_status,
                            next_review_date,
                            consecutive_proficient_count,
                            last_reviewed_at,
                            version
                        ) VALUES (?, 'MASTERED', ?, 0, NULL, 0)
                        """,
                        question.getId(),
                        TODAY
                )
        );
        SQLException sqlException = assertInstanceOf(
                SQLException.class,
                exception.getRootCause()
        );
        assertEquals(3819, sqlException.getErrorCode());
    }

    @Test
    void shouldRejectInvalidHistoryEventAtDatabaseBoundary() {
        Question question = saveQuestion("非法历史题", "408");
        saveActiveState(question, TODAY);

        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO review_record (
                            question_id,
                            event_type,
                            rating,
                            business_date,
                            occurred_at,
                            scheduled_review_date,
                            resulting_status,
                            resulting_next_review_date,
                            resulting_proficient_count
                        ) VALUES (
                            ?, 'REACTIVATION', 'FUZZY', ?, ?, NULL,
                            'ACTIVE', ?, 0
                        )
                        """,
                        question.getId(),
                        TODAY,
                        NOW,
                        TODAY
                )
        );
        SQLException sqlException = assertInstanceOf(
                SQLException.class,
                exception.getRootCause()
        );
        assertEquals(3819, sqlException.getErrorCode());
    }

    @Test
    void shouldCascadeReviewStateAndHistoryWhenQuestionIsDeleted() {
        Question question = saveQuestion("级联删除题", "408");
        saveActiveState(question, TODAY);
        reviewRecordRepository.saveAndFlush(ReviewRecord.evaluation(
                question,
                ReviewRating.NOT_KNOWN,
                TODAY,
                NOW,
                TODAY,
                ReviewStatus.ACTIVE,
                TODAY.plusDays(1),
                0
        ));
        Long questionId = question.getId();

        assertTrue(reviewStateRepository.existsById(questionId));
        assertEquals(1, reviewRecordRepository.countByQuestion_Id(questionId));

        entityManager.flush();
        entityManager.clear();

        Question persistedQuestion = questionRepository.findById(questionId)
                .orElseThrow();
        questionRepository.delete(persistedQuestion);
        questionRepository.flush();
        entityManager.clear();

        assertFalse(questionRepository.existsById(questionId));
        assertFalse(reviewStateRepository.existsById(questionId));
        assertEquals(0, reviewRecordRepository.countByQuestion_Id(questionId));
    }

    private Question saveQuestion(String text, String subject) {
        return questionRepository.saveAndFlush(new Question(
                text,
                "错误答案",
                "正确答案",
                "解析",
                "错误原因",
                subject
        ));
    }

    private QuestionReviewState saveActiveState(
            Question question,
            LocalDate nextReviewDate
    ) {
        return reviewStateRepository.saveAndFlush(
                new QuestionReviewState(question, nextReviewDate)
        );
    }

    private QuestionReviewState saveMasteredState(Question question) {
        QuestionReviewState state = new QuestionReviewState(question, TODAY);
        state.applyEvaluation(ReviewStatus.MASTERED, null, 2, NOW);
        return reviewStateRepository.saveAndFlush(state);
    }
}
