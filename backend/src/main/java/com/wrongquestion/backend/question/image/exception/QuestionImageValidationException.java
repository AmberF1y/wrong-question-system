package com.wrongquestion.backend.question.image.exception;

public class QuestionImageValidationException extends RuntimeException {

    private final String code;

    public QuestionImageValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
