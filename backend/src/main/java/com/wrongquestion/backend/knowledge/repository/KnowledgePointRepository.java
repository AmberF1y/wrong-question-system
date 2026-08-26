package com.wrongquestion.backend.knowledge.repository;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointRepository
        extends JpaRepository<KnowledgePoint, Long> {
}