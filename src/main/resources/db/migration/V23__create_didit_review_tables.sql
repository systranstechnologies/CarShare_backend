-- V23__create_didit_review_tables.sql
-- Create tables for Didit manual review management

-- Main Didit Verification Review table
CREATE TABLE IF NOT EXISTS didit_verifications (
    id CHAR(36) PRIMARY KEY COMMENT 'UUID identifier',
    user_id CHAR(36) NOT NULL UNIQUE,
    session_id VARCHAR(120) NOT NULL UNIQUE,
    verification_type VARCHAR(50) NOT NULL COMMENT 'KYC, LIVENESS, AML, etc.',
    current_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, UNDER_REVIEW, APPROVED, DECLINED, RESUBMITTED',
    approval_status VARCHAR(30) COMMENT 'PENDING, APPROVED, DECLINED for admin review',
    
    -- Document Information
    document_type VARCHAR(50) COMMENT 'Passport, License, ID, etc.',
    document_country VARCHAR(10) COMMENT 'Country code from Didit response',
    
    -- OCR and Face Match Data
    ocr_data LONGTEXT COMMENT 'Extracted data from document OCR',
    face_match_score DECIMAL(5,2) COMMENT 'Face match confidence score',
    liveness_status VARCHAR(30) COMMENT 'PASSED, FAILED, INCONCLUSIVE',
    
    -- Risk and Compliance
    aml_risk_level VARCHAR(20) COMMENT 'LOW, MEDIUM, HIGH',
    risk_flags LONGTEXT COMMENT 'JSON array of risk signals',
    verification_warnings LONGTEXT COMMENT 'JSON array of warnings from Didit',
    
    -- Approval Details
    approved_by_admin_id CHAR(36) COMMENT 'Admin user ID who approved/declined',
    approval_comment TEXT COMMENT 'Reason for approval or decline',
    approved_at TIMESTAMP NULL COMMENT 'When admin approved/declined',
    
    -- Resubmission Info
    requested_resubmission_type VARCHAR(50) COMMENT 'OCR, DOCUMENT, SELFIE, LIVENESS if resubmission requested',
    resubmission_reason TEXT,
    resubmission_requested_at TIMESTAMP NULL,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_didit_verification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_didit_verification_admin FOREIGN KEY (approved_by_admin_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_current_status (current_status),
    INDEX idx_approval_status (approval_status),
    INDEX idx_created_at (created_at),
    INDEX idx_approved_by (approved_by_admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stores Didit verification data for manual review';

-- Document Images/Evidence table
CREATE TABLE IF NOT EXISTS didit_verification_documents (
    id CHAR(36) PRIMARY KEY,
    didit_verification_id CHAR(36) NOT NULL,
    document_side VARCHAR(20) COMMENT 'FRONT, BACK, SELFIE',
    image_url VARCHAR(500) COMMENT 'URL to stored image',
    image_data_key VARCHAR(255) COMMENT 'S3 or storage key for secure access',
    processed_data LONGTEXT COMMENT 'OCR processed data for this image',
    document_width INT,
    document_height INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_document_verification FOREIGN KEY (didit_verification_id) REFERENCES didit_verifications(id) ON DELETE CASCADE,
    INDEX idx_verification_id (didit_verification_id),
    INDEX idx_document_side (document_side)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stores document images from Didit verifications';

-- Verification Events/Timeline
CREATE TABLE IF NOT EXISTS didit_verification_events (
    id CHAR(36) PRIMARY KEY,
    didit_verification_id CHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL COMMENT 'CREATED, SUBMITTED, UNDER_REVIEW, APPROVED, DECLINED, etc.',
    event_data LONGTEXT COMMENT 'JSON with event details',
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_event_verification FOREIGN KEY (didit_verification_id) REFERENCES didit_verifications(id) ON DELETE CASCADE,
    INDEX idx_verification_id (didit_verification_id),
    INDEX idx_event_type (event_type),
    INDEX idx_event_timestamp (event_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Event timeline for Didit verifications';

-- Audit Log for all review actions
CREATE TABLE IF NOT EXISTS didit_review_audit_logs (
    id CHAR(36) PRIMARY KEY,
    didit_verification_id CHAR(36) NOT NULL,
    admin_id CHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL COMMENT 'VIEWED, APPROVED, DECLINED, RESUBMITTED, COMMENTED',
    action_detail LONGTEXT COMMENT 'JSON with action specifics',
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_audit_verification FOREIGN KEY (didit_verification_id) REFERENCES didit_verifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_verification_id (didit_verification_id),
    INDEX idx_admin_id (admin_id),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Audit trail for Didit review actions';

-- Webhook Processing tracking for idempotency
CREATE TABLE IF NOT EXISTS didit_webhook_events (
    id CHAR(36) PRIMARY KEY,
    session_id VARCHAR(120) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL COMMENT 'APPROVED, DECLINED, IN_REVIEW, RESUBMITTED, EXPIRED',
    event_payload LONGTEXT NOT NULL,
    didit_timestamp TIMESTAMP,
    webhook_received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PROCESSING, COMPLETED, FAILED',
    processing_attempts INT DEFAULT 0,
    last_error TEXT,
    processed_at TIMESTAMP NULL,
    idempotency_key VARCHAR(255) UNIQUE COMMENT 'For preventing duplicate processing',
    
    INDEX idx_session_id (session_id),
    INDEX idx_status (processing_status),
    INDEX idx_received_at (webhook_received_at),
    INDEX idx_idempotency_key (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook event processing with idempotency protection';

COMMIT;
