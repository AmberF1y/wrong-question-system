package com.wrongquestion.backend.review.repository;

import com.wrongquestion.backend.review.entity.QuestionReviewState;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface QuestionReviewStateRepository
        extends JpaRepository<QuestionReviewState, Long> {

    long countByReviewStatusAndNextReviewDateLessThanEqual(
            ReviewStatus reviewStatus,
            LocalDate today
    );

    @Query("""
            select count(state)
            from QuestionReviewState state
            where state.reviewStatus = :reviewStatus
              and state.nextReviewDate <= :today
              and state.question.subject = :subject
            """)
    long countDueBySubject(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("today") LocalDate today,
            @Param("subject") String subject
    );

    @Query("""
            select state
            from QuestionReviewState state
            join fetch state.question question
            where state.reviewStatus = :reviewStatus
              and state.nextReviewDate <= :today
            order by state.nextReviewDate asc, state.questionId asc
            """)
    List<QuestionReviewState> findDue(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @Query("""
            select state
            from QuestionReviewState state
            join fetch state.question question
            where state.reviewStatus = :reviewStatus
              and state.nextReviewDate <= :today
              and question.subject = :subject
            order by state.nextReviewDate asc, state.questionId asc
            """)
    List<QuestionReviewState> findDueBySubject(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("today") LocalDate today,
            @Param("subject") String subject,
            Pageable pageable
    );

    List<QuestionReviewState> findAllByQuestionIdIn(
            Collection<Long> questionIds
    );
}
