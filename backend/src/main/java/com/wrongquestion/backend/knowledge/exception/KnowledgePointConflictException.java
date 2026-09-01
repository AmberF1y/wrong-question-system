package com.wrongquestion.backend.knowledge.exception;

public class KnowledgePointConflictException extends RuntimeException {

    private final String code;

    public KnowledgePointConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
