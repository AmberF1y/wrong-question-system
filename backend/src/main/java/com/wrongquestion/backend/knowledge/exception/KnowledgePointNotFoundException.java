package com.wrongquestion.backend.knowledge.exception;

public class KnowledgePointNotFoundException extends RuntimeException {

    public static final String CODE = "KNOWLEDGE_POINT_NOT_FOUND";

    public KnowledgePointNotFoundException(String message) {
        super(message);
    }
}
