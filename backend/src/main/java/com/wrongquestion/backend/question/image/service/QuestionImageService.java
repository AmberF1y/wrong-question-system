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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionImageService {

    private static final String REMOVE_SUCCESS_MESSAGE = "题目图片移除成功";

    private final QuestionRepository questionRepository;
    private final LocalQuestionImageStorage storage;
    private final QuestionImageTransactionCleanup transactionCleanup;

    public QuestionImageService(
            QuestionRepository questionRepository,
            LocalQuestionImageStorage storage,
            QuestionImageTransactionCleanup transactionCleanup
    ) {
        this.questionRepository = questionRepository;
        this.storage = storage;
        this.transactionCleanup = transactionCleanup;
    }

    @Transactional
    public QuestionImageResponse uploadOrReplace(
            Long questionId,
            MultipartFile file
    ) {
        Question question = findQuestion(questionId);
        StoredQuestionImage stored = storage.store(questionId, file);
        String oldRelativePath = question.getImagePath();

        transactionCleanup.registerReplacement(
                stored.relativePath(),
                oldRelativePath
        );
        question.setImagePath(stored.relativePath());
        questionRepository.saveAndFlush(question);

        return new QuestionImageResponse(
                questionId,
                stored.relativePath(),
                stored.contentType(),
                stored.size()
        );
    }

    @Transactional(readOnly = true)
    public QuestionImageContent load(Long questionId) {
        Question question = findQuestion(questionId);
        String relativePath = question.getImagePath();
        if (relativePath == null) {
            throw new QuestionImageNotFoundException("错题没有图片");
        }
        return storage.load(relativePath);
    }

    @Transactional
    public MessageResponse remove(Long questionId) {
        Question question = findQuestion(questionId);
        String relativePath = question.getImagePath();
        if (relativePath == null) {
            throw new QuestionImageConflictException("错题没有可移除的图片");
        }

        transactionCleanup.registerDeleteAfterCommit(relativePath);
        question.setImagePath(null);
        questionRepository.saveAndFlush(question);
        return new MessageResponse(REMOVE_SUCCESS_MESSAGE);
    }

    private Question findQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException("错题不存在"));
    }
}
