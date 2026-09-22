package com.wrongquestion.backend.review.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewEventType;
import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewRecord;
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

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MasteredSpotCheckControllerTest.FixedClockConfiguration.class)
class MasteredSpotCheckControllerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
    private static final Instant NOW = Instant.parse("2026-09-22T02:20:30Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionReviewStateRepository reviewStateRepository;

    @Autowired
    private ReviewRecordRepository reviewRecordRepository;

    @Test
    void shouldSelectOneMasteredQuestionAndCompleteDailySpotCheck()
            throws Exception {
        saveMasteredQuestion("第一道已掌握题", "数学");
        saveMasteredQuestion("第二道已掌握题", "数学");

        String body = mockMvc.perform(get("/api/reviews/mastered/spot-check")
                        .param("subject", "数学"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedToday").value(false))
                .andExpect(jsonPath("$.eligibleCount").value(2))
                .andExpect(jsonPath("$.cooldownDays").value(30))
                .andExpect(jsonPath("$.question.subject").value("数学"))
                .andExpect(jsonPath("$.question.correctAnswer").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        long questionId = response.path("question").path("id").asLong();

        mockMvc.perform(post(
                                "/api/reviews/{questionId}/spot-check-evaluations",
                                questionId
                        )
                        .param("subject", "数学")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"PROFICIENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("SPOT_CHECK"))
                .andExpect(jsonPath("$.rating").value("PROFICIENT"))
                .andExpect(jsonPath("$.reviewStatus").value("MASTERED"))
                .andExpect(jsonPath("$.nextReviewDate").value(nullValue()))
                .andExpect(jsonPath("$.lastReviewedAt").value(NOW.toString()));

        mockMvc.perform(get("/api/reviews/mastered/spot-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedToday").value(true))
                .andExpect(jsonPath("$.eligibleCount").value(0))
                .andExpect(jsonPath("$.question").value(nullValue()));

        assertEquals(
                1,
                reviewRecordRepository.findAll().stream()
                        .filter(record -> record.getEventType()
                                == ReviewEventType.SPOT_CHECK)
                        .count()
        );
    }

    @Test
    void shouldReturnFailedSpotCheckToDueQueue() throws Exception {
        Question question = saveMasteredQuestion("需要重新复习", "408");

        mockMvc.perform(post(
                                "/api/reviews/{questionId}/spot-check-evaluations",
                                question.getId()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"NOT_KNOWN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("SPOT_CHECK"))
                .andExpect(jsonPath("$.reviewStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.nextReviewDate").value("2026-09-23"))
                .andExpect(jsonPath("$.consecutiveProficientCount").value(0));
    }

    @Test
    void shouldBlockSpotCheckUntilDueQueueIsEmpty() throws Exception {
        Question due = saveQuestion("到期题", "数学");
        reviewStateRepository.saveAndFlush(
                new QuestionReviewState(due, TODAY)
        );
        saveMasteredQuestion("已掌握题", "数学");

        mockMvc.perform(get("/api/reviews/mastered/spot-check")
                        .param("subject", "数学"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPOT_CHECK_DUE_REVIEW_REMAINING"));
    }

    @Test
    void shouldExcludeQuestionInsideThirtyDayCooldown() throws Exception {
        Question question = saveMasteredQuestion("刚抽查过", "408");
        reviewRecordRepository.saveAndFlush(ReviewRecord.spotCheck(
                question,
                ReviewRating.PROFICIENT,
                TODAY.minusDays(29),
                NOW.minus(29, java.time.temporal.ChronoUnit.DAYS),
                ReviewStatus.MASTERED,
                null,
                2
        ));

        mockMvc.perform(get("/api/reviews/mastered/spot-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedToday").value(false))
                .andExpect(jsonPath("$.eligibleCount").value(0))
                .andExpect(jsonPath("$.question").value(nullValue()));
    }

    @Test
    void shouldRejectSecondCompletedSpotCheckOnTheSameDay() throws Exception {
        Question question = saveMasteredQuestion("每日只抽查一次", "408");
        String request = """
                {"rating":"PROFICIENT"}
                """;

        mockMvc.perform(post(
                                "/api/reviews/{questionId}/spot-check-evaluations",
                                question.getId()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                                "/api/reviews/{questionId}/spot-check-evaluations",
                                question.getId()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SPOT_CHECK_ALREADY_COMPLETED"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedSpotCheckClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));
        }
    }

    private Question saveMasteredQuestion(String text, String subject) {
        Question question = saveQuestion(text, subject);
        QuestionReviewState state = new QuestionReviewState(question, TODAY);
        state.applyEvaluation(
                ReviewStatus.MASTERED,
                null,
                2,
                NOW.minusSeconds(3600)
        );
        reviewStateRepository.saveAndFlush(state);
        return question;
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
}
