ALTER TABLE users
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    ADD COLUMN IF NOT EXISTS didit_session_id VARCHAR(120) NULL;

CREATE UNIQUE INDEX uk_users_didit_session_id ON users (didit_session_id);

CREATE TABLE IF NOT EXISTS didit_sessions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    session_id VARCHAR(120) NOT NULL UNIQUE,
    user_id CHAR(36) NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    workflow_id VARCHAR(120),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_didit_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS didit_verification_audit (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    session_id VARCHAR(120) NOT NULL,
    workflow_id VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    decision_reason VARCHAR(1000),
    raw_payload_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_didit_audit_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_didit_audit_user (user_id),
    INDEX idx_didit_audit_role_status (user_role, status),
    INDEX idx_didit_audit_session (session_id)
);
