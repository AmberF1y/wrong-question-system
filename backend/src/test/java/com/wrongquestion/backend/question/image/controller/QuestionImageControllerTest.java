package com.wrongquestion.backend.question.image.controller;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.image.exception.QuestionImageNotFoundException;
import com.wrongquestion.backend.question.image.service.LocalQuestionImageStorage;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.question.service.QuestionService;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.question-image-directory=./target/f008-controller-images"
})
class QuestionImageControllerTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A,
            0x01, 0x02, 0x03, 0x04
    };
    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8,
            (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46,
            0x49, 0x46, 0x00, 0x01
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgePointRepository knowledgePointRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionReviewStateRepository reviewStateRepository;

    @Autowired
    private QuestionService questionService;

    @Autowired
    private LocalQuestionImageStorage storage;

    @Autowired
    private Clock clock;

    private final List<Long> questionIds = new ArrayList<>();
    private final List<Long> knowledgePointIds = new ArrayList<>();

    @AfterEach
    void cleanUpDatabaseAndFiles() {
        for (Long questionId : questionIds) {
            if (questionRepository.existsById(questionId)) {
                questionService.delete(questionId);
            }
        }

        List<Long> reversedIds = new ArrayList<>(knowledgePointIds);
        Collections.reverse(reversedIds);
        for (Long knowledgePointId : reversedIds) {
            if (knowledgePointRepository.existsById(knowledgePointId)) {
                knowledgePointRepository.deleteById(knowledgePointId);
            }
        }
    }

    @Test
    void shouldUploadReadReplaceAndRemoveImage() throws Exception {
        Question question = saveQuestion("F008图片生命周期");
        MockMultipartFile png = new MockMultipartFile(
                "file",
                "untrusted.txt",
                "text/plain",
                PNG_BYTES
        );

        mockMvc.perform(multipart(
                        HttpMethod.PUT,
                        "/api/questions/{id}/image",
                        question.getId()
                ).file(png))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").value(question.getId()))
                .andExpect(jsonPath("$.imagePath").isNotEmpty())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(PNG_BYTES.length));

        String oldPath = questionRepository.findById(question.getId())
                .orElseThrow()
                .getImagePath();

        mockMvc.perform(get(
                        "/api/questions/{id}/image",
                        question.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG_BYTES))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline"
                ))
                .andExpect(header().string(
                        "X-Content-Type-Options",
                        "nosniff"
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        MockMultipartFile jpeg = new MockMultipartFile(
                "file",
                "replacement.png",
                "image/png",
                JPEG_BYTES
        );
        mockMvc.perform(multipart(
                        HttpMethod.PUT,
                        "/api/questions/{id}/image",
                        question.getId()
                ).file(jpeg))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.size").value(JPEG_BYTES.length));

        String newPath = questionRepository.findById(question.getId())
                .orElseThrow()
                .getImagePath();
        assertNotEquals(oldPath, newPath);
        assertThrows(
                QuestionImageNotFoundException.class,
                () -> storage.load(oldPath)
        );

        mockMvc.perform(delete(
                        "/api/questions/{id}/image",
                        question.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("题目图片移除成功"));

        assertNull(questionRepository.findById(question.getId())
                .orElseThrow()
                .getImagePath());
        assertThrows(
                QuestionImageNotFoundException.class,
                () -> storage.load(newPath)
        );

        mockMvc.perform(get(
                        "/api/questions/{id}/image",
                        question.getId()
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("QUESTION_IMAGE_NOT_FOUND"));

        mockMvc.perform(delete(
                        "/api/questions/{id}/image",
                        question.getId()
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("QUESTION_IMAGE_NOT_ATTACHED"));
    }

    @Test
    void shouldRejectInvalidEmptyAndOversizedImages() throws Exception {
        Question question = saveQuestion("F008图片校验");
        MockMultipartFile invalid = new MockMultipartFile(
                "file",
                "payload.svg",
                "image/svg+xml",
                "<svg></svg>".getBytes()
        );
        MockMultipartFile empty = new MockMultipartFile(
                "file",
                "empty.png",
                "image/png",
                new byte[0]
        );

        mockMvc.perform(multipart(
                        HttpMethod.PUT,
                        "/api/questions/{id}/image",
                        question.getId()
                ).file(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("QUESTION_IMAGE_UNSUPPORTED_FORMAT"));

        mockMvc.perform(multipart(
                        HttpMethod.PUT,
                        "/api/questions/{id}/image",
                        question.getId()
                ).file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_IMAGE_EMPTY"));

        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[(int) LocalQuestionImageStorage.MAX_IMAGE_SIZE_BYTES + 1]
        );
        mockMvc.perform(multipart(
                        HttpMethod.PUT,
                        "/api/questions/{id}/image",
                        question.getId()
                ).file(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code")
                        .value("QUESTION_IMAGE_TOO_LARGE"));
    }

    @Test
    void shouldReturnQuestionNotFoundBeforeWritingImage() throws Exception {
        MockMultipartFile png = new MockMultipartFile(
                "file",
                "question.png",
                "image/png",
                PNG_BYTES
        );

        mockMvc.perform(multipart(
                        HttpMethod.PUT,
                        "/api/questions/{id}/image",
                        Long.MAX_VALUE
                ).file(png))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));

        mockMvc.perform(get(
                        "/api/questions/{id}/image",
                        Long.MAX_VALUE
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    }

    @Test
    void shouldDeleteQuestionAndCommittedImageFile() throws Exception {
        Question question = saveQuestion("F008随题删除");
        MockMultipartFile png = new MockMultipartFile(
                "file",
                "question.png",
                "image/png",
                PNG_BYTES
        );
        mockMvc.perform(multipart(
                        HttpMethod.PUT,
                        "/api/questions/{id}/image",
                        question.getId()
                ).file(png))
                .andExpect(status().isOk());

        String imagePath = questionRepository.findById(question.getId())
                .orElseThrow()
                .getImagePath();

        mockMvc.perform(delete("/api/questions/{id}", question.getId()))
                .andExpect(status().isOk());

        assertFalse(questionRepository.existsById(question.getId()));
        assertThrows(
                QuestionImageNotFoundException.class,
                () -> storage.load(imagePath)
        );
    }

    private Question saveQuestion(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        KnowledgePoint root = knowledgePointRepository.saveAndFlush(
                new KnowledgePoint(prefix + "科目-" + suffix, null)
        );
        KnowledgePoint leaf = knowledgePointRepository.saveAndFlush(
                new KnowledgePoint(prefix + "知识-" + suffix, root)
        );
        knowledgePointIds.add(root.getId());
        knowledgePointIds.add(leaf.getId());

        Question question = new Question(
                prefix + "题目-" + suffix,
                "错误答案",
                "正确答案",
                "题目解析",
                "概念错误",
                root.getName()
        );
        question.addKnowledgePoint(leaf);
        Question saved = questionRepository.saveAndFlush(question);
        reviewStateRepository.saveAndFlush(new QuestionReviewState(
                saved,
                LocalDate.now(clock).plusDays(1)
        ));
        questionIds.add(saved.getId());
        return saved;
    }
}
