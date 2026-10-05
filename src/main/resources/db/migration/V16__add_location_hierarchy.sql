ALTER TABLE locations ADD COLUMN locality VARCHAR(200) NULL;
ALTER TABLE locations ADD COLUMN street VARCHAR(200) NULL;
CREATE INDEX idx_locations_hierarchy ON locations (state, district, city, locality);