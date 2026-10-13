CREATE TABLE rag_session (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_rag_session_updated (updated_at, id)
);

CREATE TABLE rag_request (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    session_id VARCHAR(36) NULL,
    kind VARCHAR(16) NOT NULL,
    question_text VARCHAR(2000) NOT NULL,
    root_id BIGINT NULL,
    review_status VARCHAR(16) NULL,
    focus_question_id BIGINT NULL,
    follow_up BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL,
    phase VARCHAR(24) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    sources_json LONGTEXT NULL,
    answer TEXT NULL,
    answer_type VARCHAR(24) NULL,
    citations_json TEXT NULL,
    warnings_json TEXT NULL,
    retrieval_method VARCHAR(24) NULL,
    error_code VARCHAR(80) NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT fk_rag_request_session FOREIGN KEY (session_id) REFERENCES rag_session(id) ON DELETE CASCADE,
    CONSTRAINT chk_rag_request_kind CHECK (kind IN ('CHAT','INDEX')),
    CONSTRAINT chk_rag_request_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT chk_rag_request_retry CHECK (retry_count>=0),
    KEY idx_rag_request_dispatch (status, created_at, id),
    KEY idx_rag_request_session (session_id, created_at, id)
);

CREATE TABLE rag_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    chunk_number INT NOT NULL,
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    document_hash CHAR(64) NOT NULL,
    embedding_json LONGTEXT NULL,
    embedding_fingerprint CHAR(64) NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_rag_chunk_question FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE,
    UNIQUE KEY uk_rag_chunk_question_number (question_id, chunk_number),
    KEY idx_rag_chunk_embedding (embedding_fingerprint),
    CONSTRAINT chk_rag_chunk_number CHECK (chunk_number>=0)
);

CREATE TABLE rag_call_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    attempt INT NOT NULL,
    retry_count INT NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL,
    error_code VARCHAR(80) NULL,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    duration_ms BIGINT NULL,
    estimated_cost DECIMAL(18,8) NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    UNIQUE KEY uk_rag_call_attempt (request_id, attempt),
    CONSTRAINT fk_rag_call_request FOREIGN KEY (request_id) REFERENCES rag_request(id) ON DELETE CASCADE,
    CONSTRAINT chk_rag_call_purpose CHECK (purpose IN ('EMBEDDING','GENERATION')),
    CONSTRAINT chk_rag_call_status CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','INTERRUPTED','CANCELLED')),
    KEY idx_rag_call_created (created_at,id)
);
