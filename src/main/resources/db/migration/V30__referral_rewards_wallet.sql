CREATE TABLE referral_profiles (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    referral_code VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_referral_profile_user UNIQUE (user_id),
    CONSTRAINT uk_referral_profile_code UNIQUE (referral_code),
    CONSTRAINT fk_referral_profile_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE referrals (
    id CHAR(36) PRIMARY KEY,
    referrer_user_id CHAR(36) NOT NULL,
    referred_user_id CHAR(36) NOT NULL,
    referred_role VARCHAR(20) NOT NULL,
    referral_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    coins_awarded BIGINT NOT NULL DEFAULT 0,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activity_completed_at TIMESTAMP NULL,
    qualified_at TIMESTAMP NULL,
    rewarded_at TIMESTAMP NULL,
    fraud_reason VARCHAR(500) NULL,
    device_fingerprint_hash VARCHAR(64) NULL,
    payment_profile_hash VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_referral_referred UNIQUE (referred_user_id),
    CONSTRAINT fk_referral_referrer FOREIGN KEY (referrer_user_id) REFERENCES users(id),
    CONSTRAINT fk_referral_referred FOREIGN KEY (referred_user_id) REFERENCES users(id),
    INDEX idx_referral_referrer_status (referrer_user_id, status),
    INDEX idx_referral_registered (registered_at)
);

CREATE TABLE user_wallets (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    available_coins BIGINT NOT NULL DEFAULT 0,
    pending_coins BIGINT NOT NULL DEFAULT 0,
    redemption_pending_coins BIGINT NOT NULL DEFAULT 0,
    total_earned_coins BIGINT NOT NULL DEFAULT 0,
    used_coins BIGINT NOT NULL DEFAULT 0,
    redeemed_coins BIGINT NOT NULL DEFAULT 0,
    expired_coins BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_wallet_user UNIQUE (user_id),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_wallet_nonnegative CHECK (available_coins >= 0 AND pending_coins >= 0 AND redemption_pending_coins >= 0)
);

CREATE TABLE wallet_transactions (
    id CHAR(36) PRIMARY KEY,
    wallet_id CHAR(36) NOT NULL,
    transaction_type VARCHAR(40) NOT NULL,
    coins_added BIGINT NOT NULL DEFAULT 0,
    coins_deducted BIGINT NOT NULL DEFAULT 0,
    balance_after BIGINT NOT NULL,
    reference_type VARCHAR(40) NULL,
    reference_id CHAR(36) NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    remarks VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_wallet_transaction_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_wallet_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES user_wallets(id),
    INDEX idx_wallet_transaction_history (wallet_id, created_at)
);

CREATE TABLE redemption_requests (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    wallet_id CHAR(36) NOT NULL,
    account_holder_name VARCHAR(120) NOT NULL,
    bank_name VARCHAR(120) NOT NULL,
    account_number_encrypted TEXT NOT NULL,
    account_number_last4 VARCHAR(4) NOT NULL,
    ifsc_encrypted TEXT NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500) NULL,
    payment_reference VARCHAR(120) NULL,
    reviewed_by CHAR(36) NULL,
    reviewed_at TIMESTAMP NULL,
    paid_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_redemption_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_redemption_wallet FOREIGN KEY (wallet_id) REFERENCES user_wallets(id),
    INDEX idx_redemption_status_created (status, created_at),
    INDEX idx_redemption_user_created (user_id, created_at)
);

CREATE TABLE redemption_history (
    id CHAR(36) PRIMARY KEY,
    redemption_id CHAR(36) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    changed_by CHAR(36) NOT NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_redemption_history_request FOREIGN KEY (redemption_id) REFERENCES redemption_requests(id),
    INDEX idx_redemption_history (redemption_id, created_at)
);

CREATE TABLE reward_settings (
    id SMALLINT PRIMARY KEY,
    referral_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    coins_per_referral BIGINT NOT NULL DEFAULT 100,
    minimum_redemption BIGINT NOT NULL DEFAULT 100,
    maximum_redemption BIGINT NOT NULL DEFAULT 10000,
    subscription_coin_percentage INT NOT NULL DEFAULT 50,
    referral_base_url VARCHAR(255) NOT NULL DEFAULT 'https://carshare247.com/register',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_reward_settings CHECK (coins_per_referral >= 0 AND minimum_redemption > 0 AND maximum_redemption >= minimum_redemption AND subscription_coin_percentage BETWEEN 0 AND 99)
);

INSERT INTO reward_settings (id) VALUES (1);

ALTER TABLE subscriptions
    ADD COLUMN coins_applied BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN gross_amount INT NULL,
    ADD COLUMN wallet_deduction_applied BOOLEAN NOT NULL DEFAULT FALSE;
