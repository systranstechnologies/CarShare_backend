ALTER TABLE rides
	ADD CONSTRAINT chk_rides_seats_valid CHECK (available_seats >= 0 AND available_seats <= total_seats);

ALTER TABLE bookings
	ADD CONSTRAINT chk_bookings_seats_positive CHECK (seats > 0);

ALTER TABLE ride_segments
	ADD CONSTRAINT chk_ride_segments_seats_valid CHECK (
		available_seats >= 0 AND available_seats <= total_seats
	);