package com.wrongquestion.backend.common.exception;

import com.wrongquestion.backend.knowledge.exception.KnowledgePointConflictException;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointNotFoundException;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointValidationException;
import com.wrongquestion.backend.question.exception.QuestionNotFoundException;
import com.wrongquestion.backend.question.exception.QuestionValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.putIfAbsent(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                )
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "请求参数校验失败",
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedRequestBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST_BODY",
                "请求体不是合法JSON",
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "请求参数格式错误：" + exception.getName(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(KnowledgePointValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessValidation(
            KnowledgePointValidationException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                KnowledgePointValidationException.CODE,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(KnowledgePointNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            KnowledgePointNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                KnowledgePointNotFoundException.CODE,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(QuestionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleQuestionNotFound(
            QuestionNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                QuestionNotFoundException.CODE,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(QuestionValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleQuestionValidation(
            QuestionValidationException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(KnowledgePointConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            KnowledgePointConflictException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_CONFLICT",
                "数据完整性约束冲突",
                request.getRequestURI(),
                null
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                path,
                fieldErrors
        );

        return ResponseEntity.status(status).body(response);
    }
}
