package com.wrongquestion.backend.review.concurrency;

import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewRating;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import com.wrongquestion.backend.review.exception.ReviewConflictException;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;
import com.wrongquestion.backend.review.repository.ReviewRecordRepository;
import com.wrongquestion.backend.review.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class ReviewConcurrencyTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionReviewStateRepository reviewStateRepository;

    @Autowired
    private ReviewRecordRepository reviewRecordRepository;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    @Test
    void shouldRejectSecondUpdateBasedOnStaleVersion() throws Exception {
        LocalDate today = LocalDate.now(clock);
        Long questionId = createDueQuestion("乐观锁实体测试", today);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Callable<Boolean> update = () -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        QuestionReviewState state = reviewStateRepository
                                .findById(questionId)
                                .orElseThrow();
                        await(barrier);
                        state.applyEvaluation(
                                ReviewStatus.ACTIVE,
                                today.plusDays(1),
                                0,
                                clock.instant()
                        );
                        reviewStateRepository.flush();
                    });
                    return true;
                } catch (OptimisticLockingFailureException exception) {
                    return false;
                }
            };

            List<Future<Boolean>> futures = List.of(
                    executor.submit(update),
                    executor.submit(update)
            );
            long successCount = futures.stream()
                    .map(this::getFuture)
                    .filter(Boolean::booleanValue)
                    .count();

            assertEquals(1, successCount);

            QuestionReviewState finalState = transactionTemplate.execute(
                    status -> reviewStateRepository.findById(questionId)
                            .orElseThrow()
            );
            assertEquals(today.plusDays(1), finalState.getNextReviewDate());
            assertEquals(1L, finalState.getVersion());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            deleteQuestion(questionId);
        }
    }

    @Test
    void shouldAdvanceOnlyOnceForConcurrentBusinessEvaluation()
            throws Exception {
        LocalDate today = LocalDate.now(clock);
        Long questionId = createDueQuestion("并发评价测试", today);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Callable<SubmissionResult> submit = () -> {
                await(barrier);
                try {
                    reviewService.evaluate(questionId, ReviewRating.NOT_KNOWN);
                    return SubmissionResult.SUCCESS;
                } catch (ReviewConflictException
                         | OptimisticLockingFailureException exception) {
                    return SubmissionResult.CONFLICT;
                }
            };

            List<Future<SubmissionResult>> futures = List.of(
                    executor.submit(submit),
                    executor.submit(submit)
            );
            List<SubmissionResult> results = futures.stream()
                    .map(this::getFuture)
                    .toList();

            assertEquals(
                    1,
                    results.stream()
                            .filter(result -> result == SubmissionResult.SUCCESS)
                            .count()
            );
            assertEquals(
                    1,
                    results.stream()
                            .filter(result -> result == SubmissionResult.CONFLICT)
                            .count()
            );

            transactionTemplate.executeWithoutResult(status -> {
                QuestionReviewState finalState = reviewStateRepository
                        .findById(questionId)
                        .orElseThrow();
                assertEquals(
                        today.plusDays(1),
                        finalState.getNextReviewDate()
                );
                assertEquals(1L, finalState.getVersion());
                assertEquals(
                        1,
                        reviewRecordRepository.countByQuestion_Id(questionId)
                );
            });
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            deleteQuestion(questionId);
        }
    }

    private Long createDueQuestion(String text, LocalDate dueDate) {
        return transactionTemplate.execute(status -> {
            Question question = questionRepository.saveAndFlush(new Question(
                    text,
                    "错误答案",
                    "正确答案",
                    "解析",
                    "错误原因",
                    "408"
            ));
            reviewStateRepository.saveAndFlush(
                    new QuestionReviewState(question, dueDate)
            );
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

    private enum SubmissionResult {
        SUCCESS,
        CONFLICT
    }
}
