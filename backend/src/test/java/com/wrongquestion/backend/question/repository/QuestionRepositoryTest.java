package com.wrongquestion.backend.question.repository;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.entity.Question;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class QuestionRepositoryTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private KnowledgePointRepository knowledgePointRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndLoadQuestionWithKnowledgePoint() {

        KnowledgePoint knowledgePoint =
                new KnowledgePoint("Repository测试知识点", null);

        knowledgePointRepository.save(knowledgePoint);

        Question question = new Question(
                "Repository测试题目",
                "错误答案",
                "正确答案",
                "测试解析",
                "概念理解错误",
                "408"
        );

        question.addKnowledgePoint(knowledgePoint);

        questionRepository.save(question);

        entityManager.flush();

        Long questionId = question.getId();

        assertNotNull(questionId);

        entityManager.clear();

        Question savedQuestion = questionRepository
                .findById(questionId)
                .orElseThrow();

        assertEquals(
                "Repository测试题目",
                savedQuestion.getQuestionText()
        );

        assertEquals(
                "408",
                savedQuestion.getSubject()
        );

        assertEquals(
                1,
                savedQuestion.getKnowledgePoints().size()
        );

        assertTrue(
                savedQuestion.getKnowledgePoints()
                        .stream()
                        .anyMatch(knowledgePointItem ->
                                knowledgePointItem
                                        .getName()
                                        .equals("Repository测试知识点")
                        )
        );
    }
}