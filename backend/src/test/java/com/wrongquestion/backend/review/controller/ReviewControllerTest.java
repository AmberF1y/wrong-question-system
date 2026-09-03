package com.wrongquestion.backend.review.controller;

import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;
import com.wrongquestion.backend.review.repository.ReviewRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ReviewControllerTest.FixedClockConfiguration.class)
class ReviewControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);
    private static final Instant NOW = Instant.parse("2026-09-03T02:20:30Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionReviewStateRepository reviewStateRepository;

    @Autowired
    private ReviewRecordRepository reviewRecordRepository;

    @Test
    void shouldReturnOldestDueQuestionAndHideAnswerFields() throws Exception {
        Question laterId = saveQuestion("同日后创建题", "408");
        Question earliest = saveQuestion("最早到期题", "408");
        Question math = saveQuestion("数学到期题", "数学");
        saveActiveState(laterId, TODAY);
        saveActiveState(earliest, TODAY.minusDays(2));
        saveActiveState(math, TODAY);

        mockMvc.perform(get("/api/reviews/due/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(3))
                .andExpect(jsonPath("$.question.id").value(earliest.getId()))
                .andExpect(jsonPath("$.question.questionText")
                        .value("最早到期题"))
                .andExpect(jsonPath("$.question.subject").value("408"))
                .andExpect(jsonPath("$.question.nextReviewDate")
                        .value("2026-09-01"))
                .andExpect(jsonPath("$.question.wrongAnswer").doesNotExist())
                .andExpect(jsonPath("$.question.correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.question.analysis").doesNotExist())
                .andExpect(jsonPath("$.question.errorReason").doesNotExist())
                .andExpect(jsonPath("$.question.knowledgePoints")
                        .doesNotExist());
    }

    @Test
    void shouldFilterDueQueueByExactSubject() throws Exception {
        Question computer = saveQuestion("408题", "408");
        Question math = saveQuestion("数学题", "数学");
        saveActiveState(computer, TODAY);
        saveActiveState(math, TODAY);

        mockMvc.perform(get("/api/reviews/due/next")
                        .param("subject", "  数学  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(1))
                .andExpect(jsonPath("$.question.id").value(math.getId()));

        mockMvc.perform(get("/api/reviews/due/next")
                        .param("subject", "英语"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(0))
                .andExpect(jsonPath("$.question").value(nullValue()));
    }

    @Test
    void shouldEvaluateQuestionAndDecreaseDueCount() throws Exception {
        Question question = saveQuestion("待评价题", "408");
        saveActiveState(question, TODAY.minusDays(1));

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", question.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"BASICALLY_MASTERED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(question.getId()))
                .andExpect(jsonPath("$.eventType").value("EVALUATION"))
                .andExpect(jsonPath("$.rating")
                        .value("BASICALLY_MASTERED"))
                .andExpect(jsonPath("$.occurredAt").value(NOW.toString()))
                .andExpect(jsonPath("$.reviewStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.nextReviewDate").value("2026-09-10"))
                .andExpect(jsonPath("$.consecutiveProficientCount").value(0))
                .andExpect(jsonPath("$.lastReviewedAt").value(NOW.toString()));

        assertEquals(
                1,
                reviewRecordRepository.countByQuestion_Id(question.getId())
        );

        mockMvc.perform(get("/api/reviews/due/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(0))
                .andExpect(jsonPath("$.question").value(nullValue()));
    }

    @Test
    void shouldRejectSequentialDuplicateEvaluation() throws Exception {
        Question question = saveQuestion("重复提交题", "408");
        saveActiveState(question, TODAY);
        String requestBody = """
                {"rating":"NOT_KNOWN"}
                """;

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", question.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", question.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_DUE"));

        assertEquals(
                1,
                reviewRecordRepository.countByQuestion_Id(question.getId())
        );
    }

    @Test
    void shouldMasterAndReactivateWhilePreservingLastReviewedAt()
            throws Exception {
        Question question = saveQuestion("重新加入题", "408");
        QuestionReviewState state = new QuestionReviewState(question, TODAY);
        Instant previousReviewAt = NOW.minusSeconds(3600);
        state.applyEvaluation(
                ReviewStatus.ACTIVE,
                TODAY,
                1,
                previousReviewAt
        );
        reviewStateRepository.saveAndFlush(state);

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", question.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"PROFICIENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("MASTERED"))
                .andExpect(jsonPath("$.nextReviewDate").value(nullValue()))
                .andExpect(jsonPath("$.consecutiveProficientCount").value(2));

        mockMvc.perform(get("/api/reviews/due/next"))
                .andExpect(jsonPath("$.dueCount").value(0));

        mockMvc.perform(post("/api/reviews/{questionId}/reactivate", question.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("REACTIVATION"))
                .andExpect(jsonPath("$.rating").value(nullValue()))
                .andExpect(jsonPath("$.reviewStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.nextReviewDate").value("2026-09-03"))
                .andExpect(jsonPath("$.consecutiveProficientCount").value(0))
                .andExpect(jsonPath("$.lastReviewedAt").value(NOW.toString()));

        assertEquals(
                2,
                reviewRecordRepository.countByQuestion_Id(question.getId())
        );
    }

    @Test
    void shouldReturnConfirmedReviewErrors() throws Exception {
        Question future = saveQuestion("未来题", "408");
        Question active = saveQuestion("活动题", "408");
        Question mastered = saveQuestion("掌握题", "408");
        saveActiveState(future, TODAY.plusDays(1));
        saveActiveState(active, TODAY);
        QuestionReviewState masteredState = new QuestionReviewState(
                mastered,
                TODAY
        );
        masteredState.applyEvaluation(ReviewStatus.MASTERED, null, 2, NOW);
        reviewStateRepository.saveAndFlush(masteredState);

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", future.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"FUZZY"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_DUE"))
                .andExpect(jsonPath("$.path").value(
                        "/api/reviews/" + future.getId() + "/evaluations"
                ));

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", mastered.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"PROFICIENT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REVIEW_ALREADY_MASTERED"));

        mockMvc.perform(post("/api/reviews/{questionId}/reactivate", active.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_MASTERED"));
    }

    @Test
    void shouldValidateRatingSubjectAndMissingQuestion() throws Exception {
        mockMvc.perform(get("/api/reviews/due/next").param("subject", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.rating", notNullValue()));

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MALFORMED_REQUEST_BODY"));

        mockMvc.perform(post("/api/reviews/{questionId}/evaluations", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"NOT_KNOWN"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedReviewClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));
        }
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
}
