package com.wrongquestion.backend.question.repository;

import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllBySubject(String subject);

    boolean existsByKnowledgePoints_Id(Long knowledgePointId);

    @Query(
            value = """
                    select question.id
                    from Question question
                    order by question.id desc
                    """,
            countQuery = """
                    select count(question)
                    from Question question
                    """
    )
    Page<Long> findPageIds(Pageable pageable);

    @Query(
            value = """
                    select question.id
                    from Question question
                    where question.subject = :subject
                    order by question.id desc
                    """,
            countQuery = """
                    select count(question)
                    from Question question
                    where question.subject = :subject
                    """
    )
    Page<Long> findPageIdsBySubject(
            @Param("subject") String subject,
            Pageable pageable
    );

    @Query(
            value = """
                    select question.id
                    from Question question, QuestionReviewState state
                    where state.questionId = question.id
                      and state.reviewStatus = :reviewStatus
                    order by question.id desc
                    """,
            countQuery = """
                    select count(question)
                    from Question question, QuestionReviewState state
                    where state.questionId = question.id
                      and state.reviewStatus = :reviewStatus
                    """
    )
    Page<Long> findPageIdsByReviewStatus(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            Pageable pageable
    );

    @Query(
            value = """
                    select question.id
                    from Question question, QuestionReviewState state
                    where state.questionId = question.id
                      and question.subject = :subject
                      and state.reviewStatus = :reviewStatus
                    order by question.id desc
                    """,
            countQuery = """
                    select count(question)
                    from Question question, QuestionReviewState state
                    where state.questionId = question.id
                      and question.subject = :subject
                      and state.reviewStatus = :reviewStatus
                    """
    )
    Page<Long> findPageIdsBySubjectAndReviewStatus(
            @Param("subject") String subject,
            @Param("reviewStatus") ReviewStatus reviewStatus,
            Pageable pageable
    );

    @Query("""
            select distinct question
            from Question question
            left join fetch question.knowledgePoints knowledgePoint
            left join fetch knowledgePoint.parent
            where question.id in :ids
            """)
    List<Question> findAllWithKnowledgePointsByIdIn(
            @Param("ids") List<Long> ids
    );

    @Query("""
            select distinct question
            from Question question
            left join fetch question.knowledgePoints knowledgePoint
            left join fetch knowledgePoint.parent
            where question.id = :id
            """)
    Optional<Question> findWithKnowledgePointsById(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query(
            value = """
                    update question
                    set updated_time = current_timestamp
                    where id = :id
                    """,
            nativeQuery = true
    )
    int touchUpdatedTime(@Param("id") Long id);
}
