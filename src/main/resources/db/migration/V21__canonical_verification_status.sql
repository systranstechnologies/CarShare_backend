-- Normalize legacy rows without implying that DIDIT was started.
UPDATE users SET verification_status = 'NOT_STARTED'
WHERE verification_status IN ('UNVERIFIED', 'PENDING', 'PENDING_VERIFICATION');

UPDATE users SET verification_status = 'APPROVED'
WHERE verification_status = 'VERIFIED';

UPDATE users SET verification_status = 'UNDER_REVIEW'
WHERE verification_status = 'IN_REVIEW';

UPDATE owner_profiles SET verification_status = 'NOT_STARTED'
WHERE verification_status IN ('UNVERIFIED', 'PENDING', 'PENDING_VERIFICATION');

UPDATE owner_profiles SET verification_status = 'APPROVED'
WHERE verification_status = 'VERIFIED';

UPDATE owner_profiles SET verification_status = 'UNDER_REVIEW'
WHERE verification_status = 'IN_REVIEW';
