package com.wrongquestion.backend.review.entity;

import com.wrongquestion.backend.question.entity.Question;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "review_record")
public class ReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private ReviewEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", length = 30)
    private ReviewRating rating;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "scheduled_review_date")
    private LocalDate scheduledReviewDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_status", nullable = false, length = 20)
    private ReviewStatus resultingStatus;

    @Column(name = "resulting_next_review_date")
    private LocalDate resultingNextReviewDate;

    @Column(name = "resulting_proficient_count", nullable = false)
    private int resultingProficientCount;

    protected ReviewRecord() {
    }

    public static ReviewRecord evaluation(
            Question question,
            ReviewRating rating,
            LocalDate businessDate,
            Instant occurredAt,
            LocalDate scheduledReviewDate,
            ReviewStatus resultingStatus,
            LocalDate resultingNextReviewDate,
            int resultingProficientCount
    ) {
        return new ReviewRecord(
                question,
                ReviewEventType.EVALUATION,
                rating,
                businessDate,
                occurredAt,
                scheduledReviewDate,
                resultingStatus,
                resultingNextReviewDate,
                resultingProficientCount
        );
    }

    public static ReviewRecord reactivation(
            Question question,
            LocalDate businessDate,
            Instant occurredAt,
            ReviewStatus resultingStatus,
            LocalDate resultingNextReviewDate,
            int resultingProficientCount
    ) {
        return new ReviewRecord(
                question,
                ReviewEventType.REACTIVATION,
                null,
                businessDate,
                occurredAt,
                null,
                resultingStatus,
                resultingNextReviewDate,
                resultingProficientCount
        );
    }

    private ReviewRecord(
            Question question,
            ReviewEventType eventType,
            ReviewRating rating,
            LocalDate businessDate,
            Instant occurredAt,
            LocalDate scheduledReviewDate,
            ReviewStatus resultingStatus,
            LocalDate resultingNextReviewDate,
            int resultingProficientCount
    ) {
        this.question = question;
        this.eventType = eventType;
        this.rating = rating;
        this.businessDate = businessDate;
        this.occurredAt = occurredAt;
        this.scheduledReviewDate = scheduledReviewDate;
        this.resultingStatus = resultingStatus;
        this.resultingNextReviewDate = resultingNextReviewDate;
        this.resultingProficientCount = resultingProficientCount;
    }

    public Long getId() {
        return id;
    }

    public Question getQuestion() {
        return question;
    }

    public ReviewEventType getEventType() {
        return eventType;
    }

    public ReviewRating getRating() {
        return rating;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LocalDate getScheduledReviewDate() {
        return scheduledReviewDate;
    }

    public ReviewStatus getResultingStatus() {
        return resultingStatus;
    }

    public LocalDate getResultingNextReviewDate() {
        return resultingNextReviewDate;
    }

    public int getResultingProficientCount() {
        return resultingProficientCount;
    }
}
