package com.wrongquestion.backend.review.entity;

import com.wrongquestion.backend.question.entity.Question;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "question_review_state")
public class QuestionReviewState {

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "consecutive_proficient_count", nullable = false)
    private int consecutiveProficientCount;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected QuestionReviewState() {
    }

    public QuestionReviewState(
            Question question,
            LocalDate initialReviewDate
    ) {
        this.question = Objects.requireNonNull(
                question,
                "question不能为空"
        );
        this.reviewStatus = ReviewStatus.ACTIVE;
        this.nextReviewDate = Objects.requireNonNull(
                initialReviewDate,
                "initialReviewDate不能为空"
        );
        this.consecutiveProficientCount = 0;
    }

    public void applyEvaluation(
            ReviewStatus resultingStatus,
            LocalDate resultingNextReviewDate,
            int resultingProficientCount,
            Instant occurredAt
    ) {
        validateState(
                resultingStatus,
                resultingNextReviewDate,
                resultingProficientCount,
                occurredAt
        );
        this.reviewStatus = resultingStatus;
        this.nextReviewDate = resultingNextReviewDate;
        this.consecutiveProficientCount = resultingProficientCount;
        this.lastReviewedAt = occurredAt;
    }

    public void reactivate(LocalDate today) {
        if (reviewStatus != ReviewStatus.MASTERED) {
            throw new IllegalStateException("只有已掌握错题可以重新加入复习");
        }
        this.reviewStatus = ReviewStatus.ACTIVE;
        this.nextReviewDate = Objects.requireNonNull(today, "today不能为空");
        this.consecutiveProficientCount = 0;
    }

    private void validateState(
            ReviewStatus status,
            LocalDate nextDate,
            int proficientCount,
            Instant reviewedAt
    ) {
        Objects.requireNonNull(status, "resultingStatus不能为空");
        Objects.requireNonNull(reviewedAt, "occurredAt不能为空");
        if (status == ReviewStatus.ACTIVE) {
            if (nextDate == null
                    || proficientCount < 0
                    || proficientCount > 1) {
                throw new IllegalArgumentException("ACTIVE复习状态不合法");
            }
            return;
        }
        if (nextDate != null || proficientCount != 2) {
            throw new IllegalArgumentException("MASTERED复习状态不合法");
        }
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Question getQuestion() {
        return question;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public int getConsecutiveProficientCount() {
        return consecutiveProficientCount;
    }

    public Instant getLastReviewedAt() {
        return lastReviewedAt;
    }

    public Long getVersion() {
        return version;
    }
}
