package com.wrongquestion.backend.question.image.service;

import com.wrongquestion.backend.common.dto.MessageResponse;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.exception.QuestionNotFoundException;
import com.wrongquestion.backend.question.image.dto.QuestionImageResponse;
import com.wrongquestion.backend.question.image.exception.QuestionImageConflictException;
import com.wrongquestion.backend.question.image.exception.QuestionImageNotFoundException;
import com.wrongquestion.backend.question.image.model.QuestionImageContent;
import com.wrongquestion.backend.question.image.model.StoredQuestionImage;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImageServiceTest {

    private static final String NEW_PATH =
            "questions/10/00000000-0000-0000-0000-000000000010.png";
    private static final String OLD_PATH =
            "questions/10/00000000-0000-0000-0000-000000000009.jpg";

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private LocalQuestionImageStorage storage;

    @Mock
    private QuestionImageTransactionCleanup transactionCleanup;

    private QuestionImageService service;

    @BeforeEach
    void setUp() {
        service = new QuestionImageService(
                questionRepository,
                storage,
                transactionCleanup
        );
    }

    @Test
    void shouldUploadImageAndRegisterReplacementCleanup() {
        Question question = question(10L, OLD_PATH);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "question.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        when(questionRepository.findById(10L))
                .thenReturn(Optional.of(question));
        when(storage.store(10L, file)).thenReturn(
                new StoredQuestionImage(NEW_PATH, "image/png", 3L)
        );

        QuestionImageResponse response = service.uploadOrReplace(10L, file);

        assertEquals(10L, response.questionId());
        assertEquals(NEW_PATH, response.imagePath());
        assertEquals("image/png", response.contentType());
        assertEquals(3L, response.size());
        assertEquals(NEW_PATH, question.getImagePath());
        verify(transactionCleanup).registerReplacement(NEW_PATH, OLD_PATH);
        verify(questionRepository).saveAndFlush(question);
    }

    @Test
    void shouldLoadStoredImageForExistingQuestion() {
        Question question = question(10L, NEW_PATH);
        QuestionImageContent content = new QuestionImageContent(
                new ByteArrayResource(new byte[]{1, 2, 3}),
                "image/png",
                3L
        );
        when(questionRepository.findById(10L))
                .thenReturn(Optional.of(question));
        when(storage.load(NEW_PATH)).thenReturn(content);

        assertSame(content, service.load(10L));
    }

    @Test
    void shouldDistinguishMissingQuestionFromQuestionWithoutImage() {
        when(questionRepository.findById(99L)).thenReturn(Optional.empty());
        when(questionRepository.findById(10L))
                .thenReturn(Optional.of(question(10L, null)));

        assertThrows(QuestionNotFoundException.class, () -> service.load(99L));
        assertThrows(
                QuestionImageNotFoundException.class,
                () -> service.load(10L)
        );
        verify(storage, never()).load(NEW_PATH);
    }

    @Test
    void shouldRemoveImageAndRegisterCommitCleanup() {
        Question question = question(10L, NEW_PATH);
        when(questionRepository.findById(10L))
                .thenReturn(Optional.of(question));

        MessageResponse response = service.remove(10L);

        assertEquals("题目图片移除成功", response.message());
        assertNull(question.getImagePath());
        verify(transactionCleanup).registerDeleteAfterCommit(NEW_PATH);
        verify(questionRepository).saveAndFlush(question);
    }

    @Test
    void shouldRejectRemovingQuestionWithoutImage() {
        Question question = question(10L, null);
        when(questionRepository.findById(10L))
                .thenReturn(Optional.of(question));

        assertThrows(
                QuestionImageConflictException.class,
                () -> service.remove(10L)
        );

        verify(transactionCleanup, never()).registerDeleteAfterCommit(NEW_PATH);
        verify(questionRepository, never()).saveAndFlush(question);
    }

    private Question question(Long id, String imagePath) {
        Question question = new Question(
                "题目",
                "错误答案",
                "正确答案",
                "解析",
                "错误原因",
                "408"
        );
        ReflectionTestUtils.setField(question, "id", id);
        question.setImagePath(imagePath);
        return question;
    }
}
