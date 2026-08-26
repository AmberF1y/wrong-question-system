package com.wrongquestion.backend.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_point")
public class KnowledgePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "name",
            nullable = false,
            length = 100
    )
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private KnowledgePoint parent;

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
    protected KnowledgePoint() {
    }

    /**
     * 业务代码创建知识点时使用。
     *
     * parent = null 表示根节点。
     */
    public KnowledgePoint(String name, KnowledgePoint parent) {
        this.name = name;
        this.parent = parent;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public KnowledgePoint getParent() {
        return parent;
    }

    public void setParent(KnowledgePoint parent) {
        this.parent = parent;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}