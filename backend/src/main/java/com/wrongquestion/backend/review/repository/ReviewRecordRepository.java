package com.wrongquestion.backend.review.repository;

import com.wrongquestion.backend.review.entity.ReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long> {

    long countByQuestion_Id(Long questionId);
}
