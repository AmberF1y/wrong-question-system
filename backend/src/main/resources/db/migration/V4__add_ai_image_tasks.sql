CREATE TABLE ai_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    status VARCHAR(24) NOT NULL,
    image_path VARCHAR(200) NOT NULL,
    image_sha256 CHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    image_size BIGINT NOT NULL,
    root_id BIGINT NULL,
    knowledge_context LONGTEXT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(100) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    round_attempt_count INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    draft_json LONGTEXT NULL,
    error_code VARCHAR(80) NULL,
    error_message VARCHAR(500) NULL,
    available_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6) NULL,
    question_id BIGINT NULL,
    KEY idx_ai_task_dispatch (status, available_at, created_at),
    KEY idx_ai_task_created (created_at, id),
    CONSTRAINT fk_ai_task_question FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE SET NULL,
    CONSTRAINT chk_ai_task_status CHECK (status IN ('QUEUED','RUNNING','RETRY_WAIT','SUCCEEDED','FAILED','CANCELLED','APPLIED')),
    CONSTRAINT chk_ai_task_attempts CHECK (attempt_count >= 0 AND retry_count >= 0 AND image_size > 0)
);

CREATE TABLE ai_call_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    attempt INT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(80) NULL,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    duration_ms BIGINT NULL,
    estimated_cost DECIMAL(18,8) NULL COMMENT '配置单价后估算，非账单；未提供用量或单价时为空',
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    UNIQUE KEY uk_ai_call_task_attempt (task_id, attempt),
    KEY idx_ai_call_created (created_at),
    CONSTRAINT fk_ai_call_task FOREIGN KEY (task_id) REFERENCES ai_task(id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_call_status CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','INTERRUPTED','CANCELLED'))
);

CREATE TABLE ai_daily_usage (
    business_date DATE NOT NULL PRIMARY KEY,
    call_count INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ai_daily_calls CHECK (call_count >= 0)
);
