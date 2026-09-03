package com.wrongquestion.backend.question.service;

import com.wrongquestion.backend.common.dto.MessageResponse;
import com.wrongquestion.backend.knowledge.dto.KnowledgePointResponse;
import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointNotFoundException;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.dto.CreateQuestionRequest;
import com.wrongquestion.backend.question.dto.QuestionDetailResponse;
import com.wrongquestion.backend.question.dto.QuestionPageResponse;
import com.wrongquestion.backend.question.dto.QuestionSummaryResponse;
import com.wrongquestion.backend.question.dto.UpdateQuestionRequest;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.exception.QuestionNotFoundException;
import com.wrongquestion.backend.question.exception.QuestionValidationException;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private static final int QUESTION_TEXT_MAX_LENGTH = 10000;
    private static final int ANSWER_MAX_LENGTH = 5000;
    private static final int ANALYSIS_MAX_LENGTH = 10000;
    private static final int ERROR_REASON_MAX_LENGTH = 2000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DELETE_SUCCESS_MESSAGE = "错题删除成功";

    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionReviewStateRepository reviewStateRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public QuestionService(
            QuestionRepository questionRepository,
            KnowledgePointRepository knowledgePointRepository,
            QuestionReviewStateRepository reviewStateRepository,
            EntityManager entityManager,
            Clock clock
    ) {
        this.questionRepository = questionRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.reviewStateRepository = reviewStateRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public QuestionDetailResponse create(CreateQuestionRequest request) {
        NormalizedFields fields = normalizeFields(
                request.questionText(),
                request.wrongAnswer(),
                request.correctAnswer(),
                request.analysis(),
                request.errorReason()
        );
        KnowledgeSelection selection = resolveKnowledgePoints(
                request.knowledgePointIds()
        );

        Question question = new Question(
                fields.questionText(),
                fields.wrongAnswer(),
                fields.correctAnswer(),
                fields.analysis(),
                fields.errorReason(),
                selection.subject()
        );
        question.replaceKnowledgePoints(selection.knowledgePoints());

        Question savedQuestion = questionRepository.saveAndFlush(question);
        entityManager.refresh(savedQuestion);
        QuestionReviewState reviewState = reviewStateRepository.saveAndFlush(
                new QuestionReviewState(
                        savedQuestion,
                        LocalDate.now(clock).plusDays(1)
                )
        );
        return toDetailResponse(savedQuestion, reviewState);
    }

    @Transactional(readOnly = true)
    public QuestionDetailResponse getById(Long id) {
        Question question = findQuestionWithKnowledgePoints(id);
        return toDetailResponse(question, findReviewState(id));
    }

    @Transactional(readOnly = true)
    public QuestionPageResponse getPage(
            int page,
            int size,
            String subject
    ) {
        return getPage(page, size, subject, null);
    }

    @Transactional(readOnly = true)
    public QuestionPageResponse getPage(
            int page,
            int size,
            String subject,
            ReviewStatus reviewStatus
    ) {
        validatePageParameters(page, size);
        String normalizedSubject = normalizeSubjectFilter(subject);
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<Long> idPage = findQuestionIdPage(
                normalizedSubject,
                reviewStatus,
                pageRequest
        );

        if (idPage.isEmpty()) {
            return new QuestionPageResponse(
                    List.of(),
                    page,
                    size,
                    idPage.getTotalElements(),
                    idPage.getTotalPages()
            );
        }

        List<Long> ids = idPage.getContent();
        Map<Long, Question> questionsById = questionRepository
                .findAllWithKnowledgePointsByIdIn(ids)
                .stream()
                .collect(Collectors.toMap(
                        Question::getId,
                        Function.identity()
                ));
        Map<Long, QuestionReviewState> statesById = reviewStateRepository
                .findAllByQuestionIdIn(ids)
                .stream()
                .collect(Collectors.toMap(
                        QuestionReviewState::getQuestionId,
                        Function.identity()
                ));

        List<QuestionSummaryResponse> items = ids.stream()
                .map(id -> {
                    Question question = questionsById.get(id);
                    if (question == null) {
                        throw new IllegalStateException("分页错题数据不完整");
                    }
                    QuestionReviewState state = statesById.get(id);
                    if (state == null) {
                        throw new IllegalStateException(
                                "错题缺少复习状态：" + id
                        );
                    }
                    return toSummaryResponse(question, state);
                })
                .toList();

        return new QuestionPageResponse(
                items,
                page,
                size,
                idPage.getTotalElements(),
                idPage.getTotalPages()
        );
    }

    @Transactional
    public QuestionDetailResponse update(
            Long id,
            UpdateQuestionRequest request
    ) {
        Question question = findQuestionWithKnowledgePoints(id);
        NormalizedFields fields = normalizeFields(
                request.questionText(),
                request.wrongAnswer(),
                request.correctAnswer(),
                request.analysis(),
                request.errorReason()
        );
        KnowledgeSelection selection = resolveKnowledgePoints(
                request.knowledgePointIds()
        );

        question.replaceEditableFields(
                fields.questionText(),
                fields.wrongAnswer(),
                fields.correctAnswer(),
                fields.analysis(),
                fields.errorReason(),
                selection.subject()
        );
        question.replaceKnowledgePoints(selection.knowledgePoints());

        questionRepository.saveAndFlush(question);
        questionRepository.touchUpdatedTime(id);
        entityManager.refresh(question);
        return toDetailResponse(question, findReviewState(id));
    }

    @Transactional
    public MessageResponse delete(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new QuestionNotFoundException("错题不存在"));

        questionRepository.delete(question);
        questionRepository.flush();
        return new MessageResponse(DELETE_SUCCESS_MESSAGE);
    }

    private NormalizedFields normalizeFields(
            String questionText,
            String wrongAnswer,
            String correctAnswer,
            String analysis,
            String errorReason
    ) {
        return new NormalizedFields(
                normalizeRequiredText(
                        questionText,
                        "题目内容",
                        QUESTION_TEXT_MAX_LENGTH
                ),
                normalizeRequiredText(
                        wrongAnswer,
                        "错误答案",
                        ANSWER_MAX_LENGTH
                ),
                normalizeRequiredText(
                        correctAnswer,
                        "正确答案",
                        ANSWER_MAX_LENGTH
                ),
                normalizeRequiredText(
                        analysis,
                        "题目解析",
                        ANALYSIS_MAX_LENGTH
                ),
                normalizeRequiredText(
                        errorReason,
                        "错误原因",
                        ERROR_REASON_MAX_LENGTH
                )
        );
    }

    private String normalizeRequiredText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null) {
            throw validation(fieldName + "不能为空");
        }

        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw validation(fieldName + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw validation(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private KnowledgeSelection resolveKnowledgePoints(List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw validation("至少选择一个知识点");
        }
        if (requestedIds.stream().anyMatch(Objects::isNull)) {
            throw validation("知识点ID不能为空");
        }

        Set<Long> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new QuestionValidationException(
                    "QUESTION_DUPLICATE_KNOWLEDGE_POINT",
                    "知识点ID不能重复"
            );
        }

        List<KnowledgePoint> foundKnowledgePoints = new ArrayList<>(
                knowledgePointRepository.findAllById(uniqueIds)
        );
        Map<Long, KnowledgePoint> knowledgePointsById = foundKnowledgePoints
                .stream()
                .collect(Collectors.toMap(
                        KnowledgePoint::getId,
                        Function.identity()
                ));

        for (Long requestedId : requestedIds) {
            if (!knowledgePointsById.containsKey(requestedId)) {
                throw new KnowledgePointNotFoundException(
                        "知识点不存在：" + requestedId
                );
            }
        }

        List<KnowledgePoint> orderedKnowledgePoints = requestedIds.stream()
                .map(knowledgePointsById::get)
                .toList();
        KnowledgePoint firstRoot = findRoot(orderedKnowledgePoints.getFirst());

        boolean crossSubject = orderedKnowledgePoints.stream()
                .map(this::findRoot)
                .anyMatch(root -> !Objects.equals(
                        root.getId(),
                        firstRoot.getId()
                ));
        if (crossSubject) {
            throw new QuestionValidationException(
                    "QUESTION_KNOWLEDGE_POINTS_CROSS_SUBJECT",
                    "一道错题关联的知识点必须属于同一科目"
            );
        }

        return new KnowledgeSelection(
                orderedKnowledgePoints,
                firstRoot.getName()
        );
    }

    private KnowledgePoint findRoot(KnowledgePoint knowledgePoint) {
        KnowledgePoint current = knowledgePoint;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    private void validatePageParameters(int page, int size) {
        if (page < 0) {
            throw validation("page不能小于0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw validation("size必须在1到100之间");
        }
    }

    private String normalizeSubjectFilter(String subject) {
        if (subject == null) {
            return null;
        }

        String normalizedSubject = subject.strip();
        if (normalizedSubject.isBlank()) {
            throw validation("subject不能为空白");
        }
        return normalizedSubject;
    }

    private Question findQuestionWithKnowledgePoints(Long id) {
        return questionRepository.findWithKnowledgePointsById(id)
                .orElseThrow(() -> new QuestionNotFoundException("错题不存在"));
    }

    private Page<Long> findQuestionIdPage(
            String subject,
            ReviewStatus reviewStatus,
            PageRequest pageRequest
    ) {
        if (subject == null && reviewStatus == null) {
            return questionRepository.findPageIds(pageRequest);
        }
        if (subject != null && reviewStatus == null) {
            return questionRepository.findPageIdsBySubject(
                    subject,
                    pageRequest
            );
        }
        if (subject == null) {
            return questionRepository.findPageIdsByReviewStatus(
                    reviewStatus,
                    pageRequest
            );
        }
        return questionRepository.findPageIdsBySubjectAndReviewStatus(
                subject,
                reviewStatus,
                pageRequest
        );
    }

    private QuestionReviewState findReviewState(Long questionId) {
        return reviewStateRepository.findById(questionId)
                .orElseThrow(() -> new IllegalStateException(
                        "错题缺少复习状态：" + questionId
                ));
    }

    private QuestionDetailResponse toDetailResponse(
            Question question,
            QuestionReviewState reviewState
    ) {
        return new QuestionDetailResponse(
                question.getId(),
                question.getQuestionText(),
                question.getWrongAnswer(),
                question.getCorrectAnswer(),
                question.getAnalysis(),
                question.getErrorReason(),
                question.getSubject(),
                question.getImagePath(),
                toKnowledgePointResponses(question),
                question.getCreatedTime(),
                question.getUpdatedTime(),
                reviewState.getReviewStatus(),
                reviewState.getNextReviewDate(),
                reviewState.getConsecutiveProficientCount(),
                reviewState.getLastReviewedAt()
        );
    }

    private QuestionSummaryResponse toSummaryResponse(
            Question question,
            QuestionReviewState reviewState
    ) {
        return new QuestionSummaryResponse(
                question.getId(),
                question.getQuestionText(),
                question.getSubject(),
                toKnowledgePointResponses(question),
                question.getCreatedTime(),
                question.getUpdatedTime(),
                reviewState.getReviewStatus(),
                reviewState.getNextReviewDate(),
                reviewState.getConsecutiveProficientCount(),
                reviewState.getLastReviewedAt()
        );
    }

    private List<KnowledgePointResponse> toKnowledgePointResponses(
            Question question
    ) {
        return question.getKnowledgePoints().stream()
                .sorted(Comparator.comparing(KnowledgePoint::getId))
                .map(knowledgePoint -> new KnowledgePointResponse(
                        knowledgePoint.getId(),
                        knowledgePoint.getName(),
                        knowledgePoint.getParent() == null
                                ? null
                                : knowledgePoint.getParent().getId()
                ))
                .toList();
    }

    private QuestionValidationException validation(String message) {
        return new QuestionValidationException("VALIDATION_FAILED", message);
    }

    private record NormalizedFields(
            String questionText,
            String wrongAnswer,
            String correctAnswer,
            String analysis,
            String errorReason
    ) {
    }

    private record KnowledgeSelection(
            List<KnowledgePoint> knowledgePoints,
            String subject
    ) {
    }
}
