package com.wrongquestion.backend.question.exception;

public class QuestionNotFoundException extends RuntimeException {

    public static final String CODE = "QUESTION_NOT_FOUND";

    public QuestionNotFoundException(String message) {
        super(message);
    }
}
