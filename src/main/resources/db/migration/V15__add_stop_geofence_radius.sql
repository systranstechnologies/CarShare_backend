ALTER TABLE ride_stops ADD COLUMN geofence_radius INT NOT NULL DEFAULT 1000;
CREATE INDEX idx_ride_stops_coordinates ON ride_stops (latitude, longitude);
CREATE INDEX idx_ride_stops_geofence ON ride_stops (geofence_radius);