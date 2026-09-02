package com.wrongquestion.backend.question.controller;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgePointRepository knowledgePointRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateQuestionThenReturnCompleteDetail() throws Exception {
        KnowledgePoint root = saveKnowledgePoint(uniqueName("F004科目"), null);
        KnowledgePoint parent = saveKnowledgePoint(uniqueName("F004父"), root);
        KnowledgePoint leaf = saveKnowledgePoint(uniqueName("F004叶"), parent);
        String questionText = uniqueName("F004创建题");

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson(
                                "  " + questionText + "  ",
                                parent.getId(),
                                leaf.getId()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.questionText").value(questionText))
                .andExpect(jsonPath("$.wrongAnswer").value("未作答"))
                .andExpect(jsonPath("$.correctAnswer").value("正确答案"))
                .andExpect(jsonPath("$.analysis").value("题目解析"))
                .andExpect(jsonPath("$.errorReason").value("概念错误"))
                .andExpect(jsonPath("$.subject").value(root.getName()))
                .andExpect(jsonPath("$.imagePath").value(nullValue()))
                .andExpect(jsonPath("$.knowledgePoints", hasSize(2)))
                .andExpect(jsonPath("$.knowledgePoints[0].id")
                        .value(parent.getId()))
                .andExpect(jsonPath("$.knowledgePoints[1].id")
                        .value(leaf.getId()))
                .andExpect(jsonPath("$.createdTime", notNullValue()))
                .andExpect(jsonPath("$.updatedTime", notNullValue()));

        Question savedQuestion = questionRepository.findAllBySubject(root.getName())
                .stream()
                .filter(question -> question.getQuestionText().equals(questionText))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/questions/{id}", savedQuestion.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedQuestion.getId()))
                .andExpect(jsonPath("$.subject").value(root.getName()))
                .andExpect(jsonPath("$.knowledgePoints", hasSize(2)));
    }

    @Test
    void shouldPageByDescendingIdAndFilterByExactSubject() throws Exception {
        KnowledgePoint firstRoot = saveKnowledgePoint(uniqueName("F004科目甲"), null);
        KnowledgePoint firstLeaf = saveKnowledgePoint(uniqueName("F004知识甲"), firstRoot);
        KnowledgePoint secondRoot = saveKnowledgePoint(uniqueName("F004科目乙"), null);
        KnowledgePoint secondLeaf = saveKnowledgePoint(uniqueName("F004知识乙"), secondRoot);
        Question oldest = saveQuestion(uniqueName("F004旧题"), firstRoot, firstLeaf);
        Question newer = saveQuestion(uniqueName("F004新题"), firstRoot, firstLeaf);
        Question newest = saveQuestion(uniqueName("F004其他题"), secondRoot, secondLeaf);

        mockMvc.perform(get("/api/questions")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.items[1].id").value(newer.getId()))
                .andExpect(jsonPath("$.items[0].knowledgePoints", hasSize(1)))
                .andExpect(jsonPath("$.items[0].wrongAnswer").doesNotExist())
                .andExpect(jsonPath("$.items[0].correctAnswer").doesNotExist())
                .andExpect(jsonPath("$.items[0].analysis").doesNotExist())
                .andExpect(jsonPath("$.items[0].errorReason").doesNotExist())
                .andExpect(jsonPath("$.items[0].imagePath").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));

        mockMvc.perform(get("/api/questions")
                        .param("subject", "  " + firstRoot.getName() + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.items[1].id").value(oldest.getId()))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/questions")
                        .param("page", "999999")
                        .param("size", "20")
                        .param("subject", firstRoot.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/questions")
                        .param("subject", uniqueName("不存在科目")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldReplaceQuestionAndAllowSwitchingSubject() throws Exception {
        KnowledgePoint oldRoot = saveKnowledgePoint(uniqueName("F004旧科目"), null);
        KnowledgePoint oldLeaf = saveKnowledgePoint(uniqueName("F004旧知识"), oldRoot);
        KnowledgePoint newRoot = saveKnowledgePoint(uniqueName("F004新科目"), null);
        KnowledgePoint newLeaf = saveKnowledgePoint(uniqueName("F004新知识"), newRoot);
        Question question = saveQuestion(uniqueName("F004待修改"), oldRoot, oldLeaf);
        question.setImagePath("images/original.png");
        questionRepository.saveAndFlush(question);
        entityManager.refresh(question);
        LocalDateTime originalCreatedTime = question.getCreatedTime();
        String updatedText = uniqueName("F004修改后");

        mockMvc.perform(put("/api/questions/{id}", question.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson(updatedText, newLeaf.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionText").value(updatedText))
                .andExpect(jsonPath("$.subject").value(newRoot.getName()))
                .andExpect(jsonPath("$.imagePath").value("images/original.png"))
                .andExpect(jsonPath("$.knowledgePoints", hasSize(1)))
                .andExpect(jsonPath("$.knowledgePoints[0].id")
                        .value(newLeaf.getId()))
                .andExpect(jsonPath("$.updatedTime", notNullValue()));

        entityManager.flush();
        entityManager.clear();

        Question updatedQuestion = questionRepository
                .findWithKnowledgePointsById(question.getId())
                .orElseThrow();
        assertEquals(updatedText, updatedQuestion.getQuestionText());
        assertEquals(newRoot.getName(), updatedQuestion.getSubject());
        assertEquals("images/original.png", updatedQuestion.getImagePath());
        assertEquals(originalCreatedTime, updatedQuestion.getCreatedTime());
        assertEquals(1, updatedQuestion.getKnowledgePoints().size());
        assertEquals(
                newLeaf.getId(),
                updatedQuestion.getKnowledgePoints().iterator().next().getId()
        );
    }

    @Test
    void shouldDeleteQuestionCascadeRelationsAndKeepKnowledgePoint()
            throws Exception {
        KnowledgePoint root = saveKnowledgePoint(uniqueName("F004删除科目"), null);
        KnowledgePoint leaf = saveKnowledgePoint(uniqueName("F004删除知识"), root);
        Question question = saveQuestion(uniqueName("F004删除题"), root, leaf);
        Long questionId = question.getId();
        Long knowledgePointId = leaf.getId();

        assertEquals(1, relationCount(questionId));

        mockMvc.perform(delete("/api/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("错题删除成功"));

        assertFalse(questionRepository.existsById(questionId));
        assertEquals(0, relationCount(questionId));
        assertTrue(knowledgePointRepository.existsById(knowledgePointId));
    }

    @Test
    void shouldReturnValidationErrorsForInvalidTextAndKnowledgePointList()
            throws Exception {
        KnowledgePoint root = saveKnowledgePoint(uniqueName("F004校验科目"), null);

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("   ", root.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.questionText")
                        .value("题目内容不能为空"));

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("题".repeat(10001), root.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJsonWithIds("题目", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJsonWithIds("题目", "[null]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturnBusinessErrorsForInvalidKnowledgePoints() throws Exception {
        KnowledgePoint firstRoot = saveKnowledgePoint(uniqueName("F004错误科目甲"), null);
        KnowledgePoint secondRoot = saveKnowledgePoint(uniqueName("F004错误科目乙"), null);

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson(
                                "重复知识点",
                                firstRoot.getId(),
                                firstRoot.getId()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "QUESTION_DUPLICATE_KNOWLEDGE_POINT"
                ));

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson(
                                "跨科目知识点",
                                firstRoot.getId(),
                                secondRoot.getId()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        "QUESTION_KNOWLEDGE_POINTS_CROSS_SUBJECT"
                ));

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("不存在知识点", Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("KNOWLEDGE_POINT_NOT_FOUND"));
    }

    @Test
    void shouldReturnNotFoundForMissingQuestionOperations() throws Exception {
        KnowledgePoint root = saveKnowledgePoint(uniqueName("F004不存在科目"), null);

        mockMvc.perform(get("/api/questions/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));

        mockMvc.perform(put("/api/questions/{id}", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionJson("不存在错题", root.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));

        mockMvc.perform(delete("/api/questions/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    @Test
    void shouldReturnBadRequestForInvalidPaginationAndMalformedJson()
            throws Exception {
        mockMvc.perform(get("/api/questions").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/questions").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/questions").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/questions").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/questions").param("subject", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MALFORMED_REQUEST_BODY"));
    }

    private KnowledgePoint saveKnowledgePoint(
            String name,
            KnowledgePoint parent
    ) {
        return knowledgePointRepository.saveAndFlush(
                new KnowledgePoint(name, parent)
        );
    }

    private Question saveQuestion(
            String questionText,
            KnowledgePoint root,
            KnowledgePoint... knowledgePoints
    ) {
        Question question = new Question(
                questionText,
                "错误答案",
                "正确答案",
                "题目解析",
                "概念错误",
                root.getName()
        );
        Arrays.stream(knowledgePoints).forEach(question::addKnowledgePoint);
        Question savedQuestion = questionRepository.saveAndFlush(question);
        entityManager.refresh(savedQuestion);
        assertNotNull(savedQuestion.getCreatedTime());
        return savedQuestion;
    }

    private int relationCount(Long questionId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from question_knowledge_point
                        where question_id = ?
                        """,
                Integer.class,
                questionId
        );
        return count == null ? 0 : count;
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String questionJson(String questionText, Long... knowledgePointIds) {
        String idsJson = Arrays.stream(knowledgePointIds)
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
        return questionJsonWithIds(questionText, idsJson);
    }

    private String questionJsonWithIds(String questionText, String idsJson) {
        return """
                {
                  "questionText": "%s",
                  "wrongAnswer": "  未作答  ",
                  "correctAnswer": "  正确答案  ",
                  "analysis": "  题目解析  ",
                  "errorReason": "  概念错误  ",
                  "knowledgePointIds": %s
                }
                """.formatted(questionText, idsJson);
    }
}
