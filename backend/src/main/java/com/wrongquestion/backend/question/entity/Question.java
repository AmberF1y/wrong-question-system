package com.wrongquestion.backend.question.entity;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "question_text",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String questionText;

    @Column(
            name = "wrong_answer",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String wrongAnswer;

    @Column(
            name = "correct_answer",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String correctAnswer;

    @Column(
            name = "analysis",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String analysis;

    @Column(
            name = "error_reason",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String errorReason;

    @Column(
            name = "subject",
            nullable = false,
            length = 50
    )
    private String subject;

    @Column(
            name = "image_path",
            length = 500
    )
    private String imagePath;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "question_knowledge_point",
            joinColumns = @JoinColumn(name = "question_id"),
            inverseJoinColumns = @JoinColumn(name = "knowledge_point_id")
    )
    private Set<KnowledgePoint> knowledgePoints = new HashSet<>();

    @Column(
            name = "created_time",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdTime;

    @Column(
            name = "updated_time",
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedTime;

    /**
     * JPA 使用的无参构造方法。
     */
    protected Question() {
    }

    /**
     * 业务代码创建错题时使用。
     */
    public Question(
            String questionText,
            String wrongAnswer,
            String correctAnswer,
            String analysis,
            String errorReason,
            String subject
    ) {
        this.questionText = questionText;
        this.wrongAnswer = wrongAnswer;
        this.correctAnswer = correctAnswer;
        this.analysis = analysis;
        this.errorReason = errorReason;
        this.subject = subject;
    }

    public Long getId() {
        return id;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getWrongAnswer() {
        return wrongAnswer;
    }

    public void setWrongAnswer(String wrongAnswer) {
        this.wrongAnswer = wrongAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public Set<KnowledgePoint> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void addKnowledgePoint(KnowledgePoint knowledgePoint) {
        this.knowledgePoints.add(knowledgePoint);
    }

    public void removeKnowledgePoint(KnowledgePoint knowledgePoint) {
        this.knowledgePoints.remove(knowledgePoint);
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}