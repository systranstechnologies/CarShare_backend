-- V22__add_onboarding_state_to_users.sql
-- Add onboarding state tracking to users table

ALTER TABLE users ADD COLUMN IF NOT EXISTS registration_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Flag to track if basic registration is complete';

ALTER TABLE users ADD COLUMN IF NOT EXISTS current_onboarding_step VARCHAR(50) COMMENT 'Current step in onboarding workflow';

ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Flag to track if full onboarding is complete';

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_onboarding_update TIMESTAMP NULL COMMENT 'Last timestamp onboarding state was updated';

-- Add index for onboarding lookups
CREATE INDEX IF NOT EXISTS idx_users_onboarding_status ON users(role, onboarding_completed, created_at);

-- Add index for subscription approval queries
CREATE INDEX IF NOT EXISTS idx_users_verification_status ON users(verification_status, created_at);

COMMIT;
