package com.wrongquestion.backend.question.exception;

public class QuestionValidationException extends RuntimeException {

    private final String code;

    public QuestionValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
