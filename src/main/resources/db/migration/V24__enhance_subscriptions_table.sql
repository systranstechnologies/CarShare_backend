-- V24__enhance_subscriptions_table.sql
-- Enhance subscriptions table with approval tracking

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, APPROVED, REJECTED';

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS admin_approval_comment TEXT COMMENT 'Reason for approval or rejection';

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS approved_by_admin_id CHAR(36) COMMENT 'Admin who approved/rejected subscription';

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP NULL COMMENT 'When subscription was approved/rejected';

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP NULL COMMENT 'When subscription was rejected';

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS utr_submitted_at TIMESTAMP NULL COMMENT 'When UTR was submitted';

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS utr_verified BOOLEAN DEFAULT FALSE COMMENT 'Whether UTR has been verified';

-- Add indexes for subscription approval queries
CREATE INDEX IF NOT EXISTS idx_subscriptions_approval_status ON subscriptions(approval_status, created_at);

CREATE INDEX IF NOT EXISTS idx_subscriptions_owner ON subscriptions(owner_id, approval_status);

-- Foreign key for admin approval
ALTER TABLE subscriptions ADD CONSTRAINT fk_subscriptions_admin 
FOREIGN KEY (approved_by_admin_id) REFERENCES users(id) ON DELETE SET NULL;

COMMIT;
