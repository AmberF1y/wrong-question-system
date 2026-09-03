package com.wrongquestion.backend.review.exception;

public class ReviewValidationException extends RuntimeException {

    public static final String CODE = "VALIDATION_FAILED";

    public ReviewValidationException(String message) {
        super(message);
    }
}
