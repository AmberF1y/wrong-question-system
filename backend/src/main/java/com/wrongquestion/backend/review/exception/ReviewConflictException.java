package com.wrongquestion.backend.review.exception;

public class ReviewConflictException extends RuntimeException {

    private final String code;

    public ReviewConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
