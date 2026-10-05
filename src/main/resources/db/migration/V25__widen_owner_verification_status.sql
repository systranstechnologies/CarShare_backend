ALTER TABLE owner_profiles
    MODIFY COLUMN verification_status VARCHAR(30) NOT NULL;

UPDATE owner_profiles
SET verification_status = 'NOT_STARTED'
WHERE verification_status IS NULL OR verification_status = '';