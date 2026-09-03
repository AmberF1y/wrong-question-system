package com.wrongquestion.backend.review.controller;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ReviewLifecycleTest.MutableClockConfiguration.class)
class ReviewLifecycleTest {

    private static final Instant INITIAL_INSTANT =
            Instant.parse("2026-09-03T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgePointRepository knowledgePointRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.setInstant(INITIAL_INSTANT);
    }

    @Test
    void shouldCompleteReviewLifecycle() throws Exception {
        String subject = "生命周期科目-"
                + UUID.randomUUID().toString().substring(0, 8);
        KnowledgePoint root = knowledgePointRepository.saveAndFlush(
                new KnowledgePoint(subject, null)
        );
        String questionText = "生命周期题-"
                + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson(questionText, root.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.nextReviewDate").value("2026-09-04"))
                .andExpect(jsonPath("$.lastReviewedAt").value(nullValue()));

        Question question = questionRepository.findAllBySubject(subject)
                .stream()
                .filter(item -> item.getQuestionText().equals(questionText))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/reviews/due/next")
                        .param("subject", subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(0));

        clock.advance(Duration.ofDays(1));

        mockMvc.perform(get("/api/reviews/due/next")
                        .param("subject", subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(1))
                .andExpect(jsonPath("$.question.id").value(question.getId()));

        mockMvc.perform(get("/api/questions/{id}", question.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctAnswer").value("正确答案"))
                .andExpect(jsonPath("$.analysis").value("解析"));

        submitProficient(question.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.nextReviewDate").value("2026-09-18"))
                .andExpect(jsonPath("$.consecutiveProficientCount").value(1));

        clock.advance(Duration.ofDays(14));

        submitProficient(question.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("MASTERED"))
                .andExpect(jsonPath("$.nextReviewDate").value(nullValue()))
                .andExpect(jsonPath("$.consecutiveProficientCount").value(2));

        mockMvc.perform(get("/api/reviews/due/next")
                        .param("subject", subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(0));

        mockMvc.perform(get("/api/questions")
                        .param("subject", subject)
                        .param("reviewStatus", "MASTERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(question.getId()));

        mockMvc.perform(post(
                        "/api/reviews/{questionId}/reactivate",
                        question.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.nextReviewDate").value("2026-09-18"))
                .andExpect(jsonPath("$.consecutiveProficientCount").value(0));

        mockMvc.perform(get("/api/reviews/due/next")
                        .param("subject", subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(1))
                .andExpect(jsonPath("$.question.id").value(question.getId()));
    }

    private org.springframework.test.web.servlet.ResultActions submitProficient(
            Long questionId
    ) throws Exception {
        return mockMvc.perform(post(
                        "/api/reviews/{questionId}/evaluations",
                        questionId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rating":"PROFICIENT"}
                        """));
    }

    private String questionJson(String questionText, Long knowledgePointId) {
        return """
                {
                  "questionText": "%s",
                  "wrongAnswer": "错误答案",
                  "correctAnswer": "正确答案",
                  "analysis": "解析",
                  "errorReason": "概念错误",
                  "knowledgePointIds": [%d]
                }
                """.formatted(questionText, knowledgePointId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableReviewClock() {
            return new MutableClock(
                    INITIAL_INSTANT,
                    ZoneId.of("Asia/Shanghai")
            );
        }
    }

    static final class MutableClock extends Clock {

        private volatile Instant instant;
        private final ZoneId zoneId;

        private MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = Objects.requireNonNull(instant);
            this.zoneId = Objects.requireNonNull(zoneId);
        }

        void setInstant(Instant instant) {
            this.instant = Objects.requireNonNull(instant);
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
