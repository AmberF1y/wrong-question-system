package com.wrongquestion.backend.knowledge.controller;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KnowledgePointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgePointRepository knowledgePointRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldCreateRootAndChildThenReturnTree() throws Exception {
        String rootName = uniqueName("F003根");
        String childName = uniqueName("F003子");

        mockMvc.perform(post("/api/knowledge-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(rootName, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(rootName))
                .andExpect(jsonPath("$.parentId").value(nullValue()));

        KnowledgePoint root = knowledgePointRepository.findAll().stream()
                .filter(item -> item.getName().equals(rootName))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/knowledge-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(childName, root.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(childName))
                .andExpect(jsonPath("$.parentId").value(root.getId()));

        mockMvc.perform(get("/api/knowledge-points/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..name", hasItems(rootName, childName)))
                .andExpect(jsonPath("$..children", hasItem(empty())));
    }

    @Test
    void shouldRenameAndMoveChildWithinSameTree() throws Exception {
        KnowledgePoint root = saveKnowledgePoint(uniqueName("F003根"), null);
        KnowledgePoint firstParent = saveKnowledgePoint(
                uniqueName("F003父一"),
                root
        );
        KnowledgePoint secondParent = saveKnowledgePoint(
                uniqueName("F003父二"),
                root
        );
        KnowledgePoint child = saveKnowledgePoint(uniqueName("F003子"), firstParent);
        String renamed = uniqueName("F003改名");

        mockMvc.perform(put("/api/knowledge-points/{id}", child.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(renamed, secondParent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed))
                .andExpect(jsonPath("$.parentId").value(secondParent.getId()));

        entityManager.flush();
        entityManager.clear();

        KnowledgePoint saved = knowledgePointRepository.findById(child.getId())
                .orElseThrow();
        assertEquals(renamed, saved.getName());
        assertEquals(secondParent.getId(), saved.getParent().getId());
    }

    @Test
    void shouldSynchronizeQuestionSubjectWhenRenameRoot() throws Exception {
        KnowledgePoint root = saveKnowledgePoint(uniqueName("F003旧科目"), null);
        Question question = saveQuestion(root.getName(), root);
        String newRootName = uniqueName("F003新科目");

        mockMvc.perform(put("/api/knowledge-points/{id}", root.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(newRootName, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newRootName));

        entityManager.flush();
        entityManager.clear();

        Question savedQuestion = questionRepository.findById(question.getId())
                .orElseThrow();
        assertEquals(newRootName, savedQuestion.getSubject());
    }

    @Test
    void shouldDeleteUnusedLeafAndReturnSuccessMessage() throws Exception {
        KnowledgePoint leaf = saveKnowledgePoint(uniqueName("F003叶子"), null);

        mockMvc.perform(delete("/api/knowledge-points/{id}", leaf.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("知识点删除成功"));

        assertFalse(knowledgePointRepository.existsById(leaf.getId()));
    }

    @Test
    void shouldReturnValidationErrorForBlankName() throws Exception {
        mockMvc.perform(post("/api/knowledge-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("   ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.path").value("/api/knowledge-points"))
                .andExpect(jsonPath("$.fieldErrors.name")
                        .value("知识点名称不能为空"));
    }

    @Test
    void shouldReturnBadRequestWhenRootNameExceedsFiftyCharacters() throws Exception {
        mockMvc.perform(post("/api/knowledge-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("根".repeat(51), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("根节点名称不能超过50个字符"));
    }

    @Test
    void shouldReturnNotFoundForMissingKnowledgePoint() throws Exception {
        mockMvc.perform(put("/api/knowledge-points/{id}", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("不存在", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code")
                        .value("KNOWLEDGE_POINT_NOT_FOUND"));
    }

    @Test
    void shouldReturnConflictForDuplicateRootName() throws Exception {
        String rootName = uniqueName("F003重复根");
        saveKnowledgePoint(rootName, null);

        mockMvc.perform(post("/api/knowledge-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(rootName, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code")
                        .value("KNOWLEDGE_POINT_NAME_CONFLICT"));
    }

    @Test
    void shouldReturnConflictForCrossTreeMove() throws Exception {
        KnowledgePoint firstRoot = saveKnowledgePoint(uniqueName("F003根一"), null);
        KnowledgePoint secondRoot = saveKnowledgePoint(uniqueName("F003根二"), null);
        KnowledgePoint child = saveKnowledgePoint(uniqueName("F003子"), firstRoot);

        mockMvc.perform(put("/api/knowledge-points/{id}", child.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(child.getName(), secondRoot.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(
                        "KNOWLEDGE_POINT_CROSS_TREE_MOVE_FORBIDDEN"
                ));
    }

    @Test
    void shouldReturnConflictWhenDeleteParentWithChildren() throws Exception {
        KnowledgePoint parent = saveKnowledgePoint(uniqueName("F003父"), null);
        saveKnowledgePoint(uniqueName("F003子"), parent);

        mockMvc.perform(delete("/api/knowledge-points/{id}", parent.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("KNOWLEDGE_POINT_HAS_CHILDREN"));
    }

    @Test
    void shouldReturnConflictWhenDeleteKnowledgePointUsedByQuestion()
            throws Exception {
        KnowledgePoint root = saveKnowledgePoint(uniqueName("F003根"), null);
        KnowledgePoint leaf = saveKnowledgePoint(uniqueName("F003叶子"), root);
        saveQuestion(root.getName(), leaf);

        mockMvc.perform(delete("/api/knowledge-points/{id}", leaf.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("KNOWLEDGE_POINT_IN_USE"));
    }

    private KnowledgePoint saveKnowledgePoint(
            String name,
            KnowledgePoint parent
    ) {
        return knowledgePointRepository.save(new KnowledgePoint(name, parent));
    }

    private Question saveQuestion(String subject, KnowledgePoint knowledgePoint) {
        Question question = new Question(
                "F003集成测试题目",
                "错误答案",
                "正确答案",
                "测试解析",
                "测试错误原因",
                subject
        );
        question.addKnowledgePoint(knowledgePoint);
        return questionRepository.save(question);
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String json(String name, Long parentId) {
        String parentValue = parentId == null ? "null" : parentId.toString();
        return """
                {
                  "name": "%s",
                  "parentId": %s
                }
                """.formatted(name, parentValue);
    }
}
