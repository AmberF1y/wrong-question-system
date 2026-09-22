package com.wrongquestion.backend.review.repository;

import com.wrongquestion.backend.review.entity.ReviewEventType;
import com.wrongquestion.backend.review.entity.ReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long> {

    long countByQuestion_Id(Long questionId);

    boolean existsByEventTypeAndBusinessDate(
            ReviewEventType eventType,
            LocalDate businessDate
    );

    boolean existsByQuestion_IdAndEventTypeAndBusinessDateGreaterThanEqual(
            Long questionId,
            ReviewEventType eventType,
            LocalDate earliestBusinessDate
    );
}
