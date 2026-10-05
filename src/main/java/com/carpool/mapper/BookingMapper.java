package com.carpool.mapper;

import com.carpool.dto.booking.BookingResponse;
import com.carpool.entity.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {
    public BookingResponse toResponse(Booking b) {
        return BookingResponse.builder()
            .id(b.getId())
            .rideId(b.getRide().getId())
            .fromLocation(b.getFromStop() != null ? b.getFromStop().getLocationName() : b.getRide().getFromLocation())
            .toLocation(b.getToStop() != null ? b.getToStop().getLocationName() : b.getRide().getToLocation())
            .passengerId(b.getPassenger().getId())
            .passengerMobile(b.getPassengerMobile())
            .passengerName(b.getPassenger().getName())
            .passengerAge(b.getPassenger().getAge())
            .seats(b.getSeats())
            .price(b.getSegmentPrice() != null ? b.getSegmentPrice() : b.getRide().getPrice())
            .totalAmount((b.getSegmentPrice() != null ? b.getSegmentPrice() : b.getRide().getPrice())
                .multiply(java.math.BigDecimal.valueOf(b.getSeats())))
            .status(b.getStatus())
            .cancellationReason(b.getCancellationReason())
            .cancellationNote(b.getCancellationNote())
            .cancelledBy(b.getCancelledBy())
            .cancelledAt(b.getCancelledAt())
            .needsRating(b.isNeedsRating())
            .rated(b.isRated())
            .createdAt(b.getCreatedAt())
            .updatedAt(b.getUpdatedAt())
            .build();
    }
}
