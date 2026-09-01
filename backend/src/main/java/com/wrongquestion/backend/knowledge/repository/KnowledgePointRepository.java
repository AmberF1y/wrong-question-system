package com.wrongquestion.backend.knowledge.repository;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnowledgePointRepository
        extends JpaRepository<KnowledgePoint, Long> {

    @Query("""
            select knowledgePoint
            from KnowledgePoint knowledgePoint
            left join fetch knowledgePoint.parent
            order by knowledgePoint.id
            """)
    List<KnowledgePoint> findAllWithParentOrderByIdAsc();

    boolean existsByParentIsNullAndName(String name);

    boolean existsByParentIsNullAndNameAndIdNot(String name, Long id);

    boolean existsByParent_IdAndName(Long parentId, String name);

    boolean existsByParent_IdAndNameAndIdNot(
            Long parentId,
            String name,
            Long id
    );

    boolean existsByParent_Id(Long parentId);
}
