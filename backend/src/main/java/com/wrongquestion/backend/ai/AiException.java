package com.wrongquestion.backend.ai;

public class AiException extends RuntimeException {
    private final String code;
    private final int status;
    private final boolean retryable;
    private Long inputTokens;
    private Long outputTokens;

    public AiException(String code, String message, int status) {
        this(code, message, status, false);
    }

    public AiException(String code, String message, int status, boolean retryable) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public AiException withUsage(Long input, Long output) {
        inputTokens = input;
        outputTokens = output;
        return this;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }
}
