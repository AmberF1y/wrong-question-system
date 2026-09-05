package com.wrongquestion.backend.question.image.exception;

public class QuestionImageStorageException extends RuntimeException {

    public static final String CODE = "QUESTION_IMAGE_STORAGE_FAILED";

    public QuestionImageStorageException(String message) {
        super(message);
    }

    public QuestionImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
