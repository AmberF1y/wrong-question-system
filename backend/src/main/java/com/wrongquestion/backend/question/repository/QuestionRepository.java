package com.wrongquestion.backend.question.repository;

import com.wrongquestion.backend.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllBySubject(String subject);

    boolean existsByKnowledgePoints_Id(Long knowledgePointId);
}
