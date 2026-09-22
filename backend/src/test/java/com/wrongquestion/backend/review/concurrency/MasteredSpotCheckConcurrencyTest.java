package com.wrongquestion.backend.review.concurrency;

import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import com.wrongquestion.backend.review.exception.ReviewConflictException;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;
import com.wrongquestion.backend.review.repository.ReviewRecordRepository;
import com.wrongquestion.backend.review.service.MasteredSpotCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class MasteredSpotCheckConcurrencyTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionReviewStateRepository reviewStateRepository;

    @Autowired
    private ReviewRecordRepository reviewRecordRepository;

    @Autowired
    private MasteredSpotCheckService masteredSpotCheckService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    @Test
    void shouldRecordOnlyOneConcurrentSpotCheck() throws Exception {
        Long questionId = createMasteredQuestion();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Callable<Boolean> submit = () -> {
                await(barrier);
                try {
                    masteredSpotCheckService.evaluateSpotCheck(
                            questionId,
                            ReviewRating.PROFICIENT,
                            null
                    );
                    return true;
                } catch (ReviewConflictException
                         | OptimisticLockingFailureException
                         | DataIntegrityViolationException exception) {
                    return false;
                }
            };

            List<Future<Boolean>> futures = List.of(
                    executor.submit(submit),
                    executor.submit(submit)
            );
            long successCount = futures.stream()
                    .map(this::getFuture)
                    .filter(Boolean::booleanValue)
                    .count();

            assertEquals(1, successCount);
            transactionTemplate.executeWithoutResult(status -> {
                assertEquals(
                        1,
                        reviewRecordRepository.countByQuestion_Id(questionId)
                );
                QuestionReviewState state = reviewStateRepository
                        .findById(questionId)
                        .orElseThrow();
                assertEquals(ReviewStatus.MASTERED, state.getReviewStatus());
                assertEquals(1L, state.getVersion());
            });
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            deleteQuestion(questionId);
        }
    }

    private Long createMasteredQuestion() {
        return transactionTemplate.execute(status -> {
            Question question = questionRepository.saveAndFlush(new Question(
                    "并发抽查题",
                    "错误答案",
                    "正确答案",
                    "解析",
                    "错误原因",
                    "408"
            ));
            QuestionReviewState state = new QuestionReviewState(
                    question,
                    LocalDate.now(clock)
            );
            state.applyEvaluation(
                    ReviewStatus.MASTERED,
                    null,
                    2,
                    clock.instant().minusSeconds(3600)
            );
            reviewStateRepository.saveAndFlush(state);
            return question.getId();
        });
    }

    private void deleteQuestion(Long questionId) {
        transactionTemplate.executeWithoutResult(status ->
                questionRepository.findById(questionId).ifPresent(question -> {
                    questionRepository.delete(question);
                    questionRepository.flush();
                })
        );
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("并发测试同步失败", exception);
        }
    }

    private <T> T getFuture(Future<T> future) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("并发测试执行失败", exception);
        }
    }
}
