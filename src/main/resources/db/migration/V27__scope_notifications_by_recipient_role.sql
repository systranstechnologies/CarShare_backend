SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = 'recipient_role');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE notifications ADD COLUMN recipient_role VARCHAR(20) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE notifications notification
JOIN users user_account ON user_account.id = notification.user_id
SET notification.recipient_role = user_account.role
WHERE notification.recipient_role IS NULL;