package com.wrongquestion.backend.knowledge.exception;

public class KnowledgePointValidationException extends RuntimeException {

    public static final String CODE = "VALIDATION_FAILED";

    public KnowledgePointValidationException(String message) {
        super(message);
    }
}
