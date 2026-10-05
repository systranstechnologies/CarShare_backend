DELETE s
FROM subscriptions s
LEFT JOIN owner_profiles o ON o.id = s.owner_id
LEFT JOIN users u ON u.id = o.user_id
LEFT JOIN subscription_plans p ON p.id = s.plan_id
WHERE o.id IS NULL OR u.id IS NULL OR p.id IS NULL;

SET @owner_fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'subscriptions'
      AND CONSTRAINT_NAME = 'fk_subscription_owner'
);

SET @owner_fk_sql = IF(
    @owner_fk_exists = 0,
    'ALTER TABLE subscriptions ADD CONSTRAINT fk_subscription_owner FOREIGN KEY (owner_id) REFERENCES owner_profiles(id)',
    'SELECT 1'
);
PREPARE owner_fk_stmt FROM @owner_fk_sql;
EXECUTE owner_fk_stmt;
DEALLOCATE PREPARE owner_fk_stmt;