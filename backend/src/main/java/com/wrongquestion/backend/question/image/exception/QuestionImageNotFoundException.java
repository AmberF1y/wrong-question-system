package com.wrongquestion.backend.question.image.exception;

public class QuestionImageNotFoundException extends RuntimeException {

    public static final String CODE = "QUESTION_IMAGE_NOT_FOUND";

    public QuestionImageNotFoundException(String message) {
        super(message);
    }
}
