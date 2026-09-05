package com.wrongquestion.backend.question.image.exception;

public class QuestionImageConflictException extends RuntimeException {

    public static final String CODE = "QUESTION_IMAGE_NOT_ATTACHED";

    public QuestionImageConflictException(String message) {
        super(message);
    }
}
