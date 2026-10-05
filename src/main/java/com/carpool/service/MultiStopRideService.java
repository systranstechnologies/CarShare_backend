package com.carpool.service;

import com.carpool.dto.PageMeta;
import com.carpool.dto.ride.CreateMultiStopRideRequest;
import com.carpool.dto.ride.RideCreateRequest;
import com.carpool.dto.ride.RideDetailsDTO;
import com.carpool.dto.ride.RideResponse;
import com.carpool.dto.ride.RideSearchRequest;
import com.carpool.dto.ride.RideSearchResultDTO;
import com.carpool.entity.OwnerProfile;
import com.carpool.entity.Booking;
import com.carpool.entity.PricingType;
import com.carpool.entity.Ride;
import com.carpool.entity.RideSegment;
import com.carpool.entity.RideSegmentBooking;
import com.carpool.entity.RideStatus;
import com.carpool.entity.RideStop;
import com.carpool.entity.Role;
import com.carpool.exception.AppException;
import com.carpool.mapper.RideMapper;
import com.carpool.repository.BookingRepository;
import com.carpool.repository.OwnerProfileRepository;
import com.carpool.repository.RideRepository;
import com.carpool.repository.RideSegmentBookingRepository;
import com.carpool.repository.RideSegmentRepository;
import com.carpool.repository.RideStopRepository;
import com.carpool.repository.SubscriptionRepository;
import com.carpool.entity.SubscriptionStatus;
import com.carpool.entity.VerificationStatus;
import com.carpool.security.AppUserPrincipal;
import com.carpool.security.AuthFacade;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing rides with multi-stop route and segmented pricing support.
 * 
 * Responsibilities:
 * - Create simple (point-to-point) and multi-stop rides
 * - Validate ride routes and stops
 * - Generate segments for multi-stop rides
 * - Calculate prices (fixed or segmented)
 * - Search for matching rides with segment-level seat availability
 * - Manage ride status and cancellation
 */
@Service
@RequiredArgsConstructor
public class MultiStopRideService {

    private static final Logger log = LoggerFactory.getLogger(MultiStopRideService.class);
    private static final double ROUTE_MATCH_RADIUS_METERS = 15_000d;

    private final RideRepository rideRepository;
    private final RideStopRepository rideStopRepository;
    private final RideSegmentRepository rideSegmentRepository;
    private final RideSegmentBookingRepository rideSegmentBookingRepository;
    private final OwnerProfileRepository ownerRepository;
    private final BookingRepository bookingRepository;
    private final RideMapper rideMapper;
    private final AuthFacade authFacade;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SubscriptionRepository subscriptionRepository;
    private final com.carpool.repository.UserRepository userRepository;
    private final GeoFenceService geoFenceService;

    // ========== SIMPLE RIDE CREATION (Legacy) ==========

    /**
     * Create a simple point-to-point ride (legacy functionality).
     */
    @Transactional
    public RideResponse create(RideCreateRequest request) {
        log.debug("Creating simple ride from {} to {} on {}", request.getFromLocation(), request.getToLocation(), request.getDate());

        AppUserPrincipal principal = authFacade.currentUser();
        OwnerProfile owner = ownerRepository.findByUserId(principal.getUserId())
            .orElseThrow(() -> new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Owner profile required"));

        if (!owner.isVerified() || !subscriptionRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream().anyMatch(s -> s.getStatus() == SubscriptionStatus.PAID)) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin-approved subscription and owner verification are required to create rides");
        }

        if (request.getDate().isBefore(LocalDate.now())) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "SEMANTIC_ERROR", "Ride date cannot be in past");
        }

        Ride ride = new Ride();
        ride.setOwner(owner);
        ride.setFromLocation(request.getFromLocation());
        ride.setToLocation(request.getToLocation());
        ride.setDate(request.getDate());
        ride.setStartTime(request.getStartTime());
        ride.setEndTime(request.getEndTime());
        ride.setPrice(request.getPrice());
        ride.setCarModel(request.getCarModel());
        ride.setTotalSeats(request.getTotalSeats());
        ride.setAvailableSeats(request.getTotalSeats());
        ride.setFemaleOnly(request.getFemaleOnly() != null && request.getFemaleOnly());
        ride.setStatus(RideStatus.ACTIVE);
        ride.setMultiStop(false);
        ride.setPricingType(PricingType.FIXED);

        Ride saved = rideRepository.save(ride);
        log.debug("Simple ride created with ID: {}", saved.getId());

        notifyRideCreated(principal.getUserId(), saved);
        return rideMapper.toResponse(saved);
    }

    // ========== MULTI-STOP RIDE CREATION ==========

    /**
     * Create a multi-stop ride with segmented pricing.
     */
    @Transactional
    public RideDetailsDTO createMultiStopRide(CreateMultiStopRideRequest request) {
        log.debug("Creating multi-stop ride with {} stops", request.getStops().size());

        AppUserPrincipal principal = authFacade.currentUser();
        OwnerProfile owner = ownerRepository.findByUserId(principal.getUserId())
            .orElseThrow(() -> new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Owner profile required"));

        if (!owner.isVerified() || !subscriptionRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId()).stream().anyMatch(s -> s.getStatus() == SubscriptionStatus.PAID)) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin-approved subscription and owner verification are required to create rides");
        }
        if (rideRepository.existsByOwnerIdAndStatus(owner.getId(), RideStatus.ACTIVE)) {
            throw new AppException(HttpStatus.CONFLICT, "ACTIVE_RIDE_EXISTS", "Complete or cancel your active ride before posting another ride");
        }

        // Validate request
        validateMultiStopRideRequest(request);

        // Create ride entity
        Ride ride = new Ride();
        var firstStop = request.getStops().get(0);
        var lastStop = request.getStops().get(request.getStops().size() - 1);
        BigDecimal routePrice = calculateTotalRoutePrice(request);

        // Validate critical fields before setting
        if (firstStop.getLocationName() == null || firstStop.getLocationName().isBlank()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOCATION", "First stop location is required");
        }
        if (lastStop.getLocationName() == null || lastStop.getLocationName().isBlank()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOCATION", "Last stop location is required");
        }
        if (firstStop.getDepartureTime() == null) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME", "First stop departure time is required");
        }
        if (lastStop.getArrivalTime() == null) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME", "Last stop arrival time is required");
        }

        ride.setOwner(owner);
        ride.setFromLocation(firstStop.getLocationName());
        ride.setToLocation(lastStop.getLocationName());
        ride.setDate(request.getDate());
        ride.setStartTime(firstStop.getDepartureTime());
        ride.setEndTime(lastStop.getArrivalTime());
        ride.setPrice(routePrice);
        ride.setCarModel(request.getCarModel());
        ride.setTotalSeats(request.getTotalSeats());
        ride.setAvailableSeats(request.getTotalSeats());
        ride.setFemaleOnly(request.getFemaleOnly() != null && request.getFemaleOnly());
        ride.setStatus(RideStatus.ACTIVE);
        ride.setMultiStop(true);
        ride.setTotalStops(request.getStops().size());
        ride.setPricingType(request.getPricingType());

        // Save ride first
        ride = rideRepository.save(ride);

        // Create stops
        List<RideStop> stops = createRideStops(ride, request.getStops());

        // Create segments
        List<RideSegment> segments = createRideSegments(ride, stops, request.getSegmentPrices(), request.getPricingType());

        ride.setStops(stops);
        ride.setSegments(segments);
        ride = rideRepository.save(ride);

        log.debug("Multi-stop ride created with ID: {}, {} stops, {} segments", ride.getId(), stops.size(), segments.size());

        notifyRideCreated(principal.getUserId(), ride);
        return mapToRideDetailsDTO(ride);
    }

    /**
     * Validate multi-stop ride request.
     * Throws AppException if any validation fails.
     */
    private void validateMultiStopRideRequest(CreateMultiStopRideRequest request) {
        // Validate request object itself
        if (request == null) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "NULL_REQUEST", "Request cannot be null");
        }
        if (request.getStops() == null) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "NULL_STOPS", "Stops list cannot be null");
        }
        // Check minimum stops
        if (request.getStops().size() < 2) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STOPS", "Minimum 2 stops required");
        }

        // Check date
        if (request.getDate().isBefore(LocalDate.now())) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DATE", "Ride date cannot be in past");
        }

        // Check for duplicate locations
        long uniqueLocations = request.getStops().stream()
            .map(s -> s.getLocationName().toLowerCase())
            .distinct()
            .count();
        if (uniqueLocations != request.getStops().size()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "DUPLICATE_STOPS", "Duplicate stop locations not allowed");
        }

        // Validate stop times
        for (int i = 0; i < request.getStops().size(); i++) {
            var stop = request.getStops().get(i);
            String stopLabel = (i == 0 ? "Origin" : i == request.getStops().size() - 1 ? "Destination" : "Intermediate") + " stop " + (i + 1);
            
            // Validate location name
            if (stop.getLocationName() == null || stop.getLocationName().isBlank()) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOCATION", stopLabel + ": location name is required");
            }
            if (stop.getLatitude() == null || stop.getLongitude() == null) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOCATION", stopLabel + ": select a location from the geo suggestions");
            }

            // First stop must have departure time
            if (i == 0) {
                if (stop.getDepartureTime() == null) {
                    throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME", stopLabel + ": departure time is required");
                }
                // First stop must NOT have arrival time
                if (stop.getArrivalTime() != null) {
                    log.warn("First stop has arrival time set; ignoring");
                }
            }
            // Last stop must have arrival time
            else if (i == request.getStops().size() - 1) {
                if (stop.getArrivalTime() == null) {
                    throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME", stopLabel + ": arrival time is required");
                }
                // Last stop must NOT have departure time
                if (stop.getDepartureTime() != null) {
                    log.warn("Last stop has departure time set; ignoring");
                }
            }
            // Intermediate stops must have both arrival and departure
            else {
                if (stop.getArrivalTime() == null) {
                    throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME", stopLabel + ": arrival time is required");
                }
                if (stop.getDepartureTime() == null) {
                    throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME", stopLabel + ": departure time is required");
                }
                // Arrival must be before departure
                if (stop.getArrivalTime().isAfter(stop.getDepartureTime()) || stop.getArrivalTime().equals(stop.getDepartureTime())) {
                    throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME", stopLabel + ": arrival time must be before departure time");
                }
            }
        }

        // Validate time sequence across stops
        for (int i = 1; i < request.getStops().size(); i++) {
            var prevStop = request.getStops().get(i - 1);
            var currStop = request.getStops().get(i);

            // Current arrival must be after previous departure
            if (prevStop.getDepartureTime() == null) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME_SEQUENCE", 
                    "Stop " + i + ": previous stop departure time is missing");
            }
            if (currStop.getArrivalTime() == null) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME_SEQUENCE", 
                    "Stop " + (i + 1) + ": arrival time is missing");
            }
            
            if (currStop.getArrivalTime().isBefore(prevStop.getDepartureTime()) || 
                currStop.getArrivalTime().equals(prevStop.getDepartureTime())) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME_SEQUENCE", 
                    "Stop " + (i + 1) + ": arrival time must be after departure from previous stop");
            }
        }

        // Validate prices
        if (request.getSegmentPrices() != null) {
            for (var segmentPrice : request.getSegmentPrices()) {
                if (segmentPrice.getPrice() != null && segmentPrice.getPrice().signum() <= 0) {
                    throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PRICE", "Price must be greater than 0");
                }
            }
        }
    }

    /**
     * Create RideStop entities from the request.
     */
    private List<RideStop> createRideStops(Ride ride, List<com.carpool.dto.ride.RideStopDTO> stopDTOs) {
        List<RideStop> stops = new ArrayList<>();

        for (int i = 0; i < stopDTOs.size(); i++) {
            var stopDTO = stopDTOs.get(i);
            
            // Calculate stop duration in minutes
            Integer stopDurationMinutes = null;
            if (stopDTO.getStopDurationMinutes() != null && stopDTO.getStopDurationMinutes() > 0) {
                // Use provided duration if available
                stopDurationMinutes = stopDTO.getStopDurationMinutes();
            } else if (i > 0 && i < stopDTOs.size() - 1) {
                // For intermediate stops, calculate duration as departure - arrival
                if (stopDTO.getArrivalTime() != null && stopDTO.getDepartureTime() != null) {
                    stopDurationMinutes = (int) ChronoUnit.MINUTES.between(
                        stopDTO.getArrivalTime(), 
                        stopDTO.getDepartureTime()
                    );
                } else {
                    stopDurationMinutes = 0; // Default to 0 if times not available
                }
            } else {
                // For first and last stops, default to 0 or calculate stay time
                stopDurationMinutes = 0;
            }
            
            RideStop stop = RideStop.builder()
                .ride(ride)
                .stopOrder(i)
                .locationName(stopDTO.getLocationName())
                .latitude(stopDTO.getLatitude())
                .longitude(stopDTO.getLongitude())
                .geofenceRadius(stopDTO.getGeofenceRadius() == null ? 1000 : stopDTO.getGeofenceRadius())
                .arrivalTime(stopDTO.getArrivalTime())
                .departureTime(stopDTO.getDepartureTime())
                .stopDurationMinutes(stopDurationMinutes)
                .build();
            stops.add(stop);
        }

        return rideStopRepository.saveAll(stops);
    }

    /** Create one inventory segment for each pair of adjacent stops. */
    private List<RideSegment> createRideSegments(Ride ride, List<RideStop> stops, 
                                                 List<CreateMultiStopRideRequest.SegmentPriceDTO> pricingRules,
                                                 PricingType pricingType) {
        List<RideSegment> segments = new ArrayList<>();
        int segmentOrder = 0;

        // Inventory is tracked on adjacent legs only. Longer journeys use multiple legs.
        for (int i = 0; i < stops.size(); i++) {
            int j = i + 1;
            if (j >= stops.size()) continue;
                RideStop fromStop = stops.get(i);
                RideStop toStop = stops.get(j);

                // Calculate price
                BigDecimal price = calculateSegmentPrice(pricingRules, pricingType, i, j, stops);

                if (price == null || price.signum() <= 0) {
                    throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PRICE", 
                        "Price for segment " + fromStop.getLocationName() + " → " + toStop.getLocationName() + " is not defined or invalid");
                }

                // Calculate distance and duration (optional)
                Integer durationMinutes = null;
                if (fromStop.getDepartureTime() != null && toStop.getArrivalTime() != null) {
                    durationMinutes = (int) ChronoUnit.MINUTES.between(fromStop.getDepartureTime(), toStop.getArrivalTime());
                }

                RideSegment segment = RideSegment.builder()
                    .ride(ride)
                    .fromStop(fromStop)
                    .toStop(toStop)
                    .segmentOrder(segmentOrder++)
                    .price(price)
                    .availableSeats(ride.getTotalSeats())
                    .totalSeats(ride.getTotalSeats())
                    .durationMinutes(durationMinutes)
                    .build();

                segments.add(segment);
        }

        return rideSegmentRepository.saveAll(segments);
    }

    /**
     * Calculate price for a segment based on pricing rules.
     */
    private BigDecimal calculateSegmentPrice(List<CreateMultiStopRideRequest.SegmentPriceDTO> pricingRules,
                                            PricingType pricingType, int fromStopIndex, int toStopIndex,
                                            List<RideStop> stops) {
        if (pricingRules == null || pricingRules.isEmpty()) {
            return null;
        }

        for (var rule : pricingRules) {
            // Exact match
            if (rule.getFromStopOrder() == fromStopIndex && rule.getToStopOrder() == toStopIndex) {
                // Check if price is provided directly
                if (rule.getPrice() != null && rule.getPrice().signum() > 0) {
                    return rule.getPrice();
                }

                // Try to calculate from distance and price per KM
                if (rule.getDistanceKm() != null && rule.getPricePerKm() != null) {
                    BigDecimal baseFare = rule.getBaseFare() != null ? rule.getBaseFare() : BigDecimal.ZERO;
                    return baseFare.add(rule.getDistanceKm().multiply(rule.getPricePerKm()));
                }
            }
        }

        return null;
    }

    private BigDecimal calculateTotalRoutePrice(CreateMultiStopRideRequest request) {
        if (request.getSegmentPrices() == null || request.getSegmentPrices().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return request.getSegmentPrices().stream()
            .map(CreateMultiStopRideRequest.SegmentPriceDTO::getPrice)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ========== MULTI-STOP RIDE SEARCH ==========

    /**
     * Search for available rides matching passenger criteria.
     * Returns rides where:
     * 1. From location exists as a stop
     * 2. To location exists as a stop
     * 3. From stop order < To stop order
     * 4. All segments between them have sufficient seats
     */
    @Transactional(readOnly = true)
    public Map<String, Object> searchMultiStopRides(RideSearchRequest request) {
        requireApprovedPassenger();
        UUID requesterId = authFacade.currentUser().getUserId();
        String requesterGender = userRepository.findById(requesterId)
            .map(user -> user.getGender())
            .orElse(null);
        log.debug("Searching multi-stop rides from {} to {} on {} for {} seats",
            request.getFromLocation(), request.getToLocation(), request.getDate(), request.getSeats());

        Page<Ride> rides = rideRepository.findActiveMultiStopRides(
            request.getDate(),
            PageRequest.of(0, Math.min(Math.max(request.getSize() * 10, 100), 1000), Sort.by("date"))
        );

        List<RideSearchResultDTO> results = new ArrayList<>();

        for (Ride ride : rides.getContent()) {
            try {
                if (ride.getOwner().getUser().getId().equals(requesterId)) {
                    continue;
                }
                if (ride.isFemaleOnly() && !"female".equalsIgnoreCase(requesterGender)) {
                    continue;
                }
                List<RideStop> rideStops = rideStopRepository.findByRideIdOrderByStopOrder(ride.getId());
                RideStop fromStop = findMatchingStop(rideStops, request.getFromLocation(), request.getFromLatitude(), request.getFromLongitude());
                RideStop toStop = findMatchingStop(rideStops, request.getToLocation(), request.getToLatitude(), request.getToLongitude());
                if (fromStop == null || toStop == null) continue;

                // Validate stop order
                if (fromStop.getStopOrder() >= toStop.getStopOrder()) {
                    continue;
                }

                // Pending requests do not consume capacity. Full rides are returned so the client can show FULL.
                int availableSeats = calculateAcceptedAvailability(ride, fromStop.getStopOrder(), toStop.getStopOrder());
                boolean seatsAvailable = availableSeats >= request.getSeats();

                // Get segment price
                List<RideSegment> journeySegments = rideSegmentRepository.findOverlappingSegments(
                    ride.getId(), fromStop.getStopOrder(), toStop.getStopOrder());
                if (journeySegments.size() != toStop.getStopOrder() - fromStop.getStopOrder()) {
                    continue;
                }

                RideSegment segment = journeySegments.get(0);
                BigDecimal journeyPrice = journeySegments.stream()
                    .map(RideSegment::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Always load route stops in their persisted route order.
                ride.setStops(rideStopRepository.findByRideIdOrderByStopOrder(ride.getId()));

                // Build result
                RideSearchResultDTO result = buildSearchResult(ride, fromStop, toStop, segment, request.getSeats());
                result.setPrice(journeyPrice);
                result.setAvailableSeats(availableSeats);
                results.add(result);

            } catch (Exception e) {
                log.warn("Error processing ride {} for search: {}", ride.getId(), e.getMessage());
            }
        }

        return Map.of(
            "items", results,
            "meta", PageMeta.builder()
                .page(request.getPage())
                .size(request.getSize())
                .totalElements(results.size())
                .totalPages((results.size() + request.getSize() - 1) / request.getSize())
                .build()
        );
    }

    private RideStop findMatchingStop(List<RideStop> stops, String name, BigDecimal latitude, BigDecimal longitude) {
        if (latitude != null && longitude != null) {
            return stops.stream()
                .filter(stop -> stop.getLatitude() != null && stop.getLongitude() != null)
                .filter(stop -> geoFenceService.distanceMeters(latitude, longitude, stop.getLatitude(), stop.getLongitude()) <= ROUTE_MATCH_RADIUS_METERS)
                .min(Comparator.comparingDouble(stop -> geoFenceService.distanceMeters(latitude, longitude, stop.getLatitude(), stop.getLongitude())))
                .orElse(null);
        }
        return stops.stream()
            .filter(stop -> stop.getLocationName() != null && stop.getLocationName().trim().equalsIgnoreCase(name == null ? "" : name.trim()))
            .findFirst().orElse(null);
    }

    private void requireApprovedPassenger() {
        AppUserPrincipal principal = authFacade.currentUser();
        if (principal.getRole() != Role.PASSENGER) {
            throw new AppException(HttpStatus.FORBIDDEN, "PASSENGER_REQUIRED", "Passenger access is required");
        }
        userRepository.findById(principal.getUserId()).ifPresentOrElse(user -> {
            VerificationStatus status = user.getVerificationStatus() == null
                ? VerificationStatus.NOT_STARTED : user.getVerificationStatus().canonical();
            if (status != VerificationStatus.APPROVED) throw new AppException(HttpStatus.FORBIDDEN,
                "IDENTITY_VERIFICATION_REQUIRED", "Complete identity verification before searching rides");
        }, () -> { throw new AppException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Authenticated user no longer exists"); });
    }

    /**
     * Check if all segments between two stops have sufficient seats.
     */
    private boolean checkSegmentAvailability(UUID rideId, int fromStopOrder, int toStopOrder, int requiredSeats) {
        Ride ride = rideRepository.findById(rideId).orElseThrow();
        return calculateAcceptedAvailability(ride, fromStopOrder, toStopOrder) >= requiredSeats;
    }

    private int calculateAcceptedAvailability(Ride ride, int fromStopOrder, int toStopOrder) {
        List<Booking> reserved = bookingRepository.findByRideIdAndStatusIn(
            ride.getId(), List.of(com.carpool.entity.BookingStatus.PENDING, com.carpool.entity.BookingStatus.ACCEPTED));
        int minimumAvailable = ride.getTotalSeats();
        for (int segmentOrder = fromStopOrder; segmentOrder < toStopOrder; segmentOrder++) {
            final int currentSegmentOrder = segmentOrder;
            int occupied = reserved.stream()
                .filter(booking -> booking.getFromStop() == null || booking.getToStop() == null
                    || (booking.getFromStop().getStopOrder() <= currentSegmentOrder
                        && booking.getToStop().getStopOrder() > currentSegmentOrder))
                .mapToInt(Booking::getSeats)
                .sum();
            minimumAvailable = Math.min(minimumAvailable, Math.max(0, ride.getTotalSeats() - occupied));
        }
        return minimumAvailable;
    }

    /**
     * Build RideSearchResultDTO from ride and segment information.
     */
    private RideSearchResultDTO buildSearchResult(Ride ride, RideStop fromStop, RideStop toStop, 
                                                 RideSegment segment, int requestedSeats) {
        String duration = formatDuration(fromStop.getDepartureTime(), toStop.getArrivalTime());
        String routePreview = buildRoutePreview(ride.getStops());

        return RideSearchResultDTO.builder()
            .rideId(ride.getId())
            .driverId(ride.getOwner().getUser().getId())
            .driverName(ride.getOwner().getUser().getFullName())
            .driverAverageRating(BigDecimal.ZERO) // TODO: Fetch from ratings
            .driverRatingsCount(0L) // TODO: Fetch from ratings
            .vehicleModel(ride.getCarModel())
            .travelDate(ride.getDate().toString())
            .fromLocation(fromStop.getLocationName())
            .toLocation(toStop.getLocationName())
            .departureTime(fromStop.getDepartureTime())
            .arrivalTime(toStop.getArrivalTime())
            .travelDuration(duration)
            .price(segment.getPrice())
            .availableSeats(segment.getAvailableSeats())
            .totalSeats(ride.getTotalSeats())
            .routePreview(routePreview)
            .routeStops(buildRouteStopDetails(ride.getStops(), fromStop.getId(), toStop.getId()))
            .femaleOnly(ride.isFemaleOnly())
            .distanceKm(segment.getDistanceKm())
            .build();
    }

    /**
     * Build route preview string.
     */
    private String buildRoutePreview(List<RideStop> stops) {
        return stops.stream()
            .map(RideStop::getLocationName)
            .collect(Collectors.joining(" → "));
    }

    /**
     * Build detailed route stop information.
     */
    private List<RideSearchResultDTO.RouteStopDetail> buildRouteStopDetails(List<RideStop> stops, UUID fromStopId, UUID toStopId) {
        return stops.stream()
            .map(stop -> RideSearchResultDTO.RouteStopDetail.builder()
                .stopOrder(stop.getStopOrder())
                .locationName(stop.getLocationName())
                .arrivalTime(stop.getArrivalTime())
                .departureTime(stop.getDepartureTime())
                .isFromStop(stop.getId().equals(fromStopId))
                .isToStop(stop.getId().equals(toStopId))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Format duration between two times.
     */
    private String formatDuration(LocalTime start, LocalTime end) {
        if (start == null || end == null) return "N/A";
        long minutes = ChronoUnit.MINUTES.between(start, end);
        long hours = minutes / 60;
        long mins = minutes % 60;
        return String.format("%dh %dm", hours, mins);
    }

    // ========== RIDE DETAIL RETRIEVAL ==========

    /**
     * Get complete ride details including all stops and segments.
     */
    @Transactional(readOnly = true)
    public RideDetailsDTO getRideDetails(UUID rideId) {
        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ride not found"));

        return mapToRideDetailsDTO(ride);
    }

    /**
     * Map Ride entity to RideDetailsDTO.
     */
    private RideDetailsDTO mapToRideDetailsDTO(Ride ride) {
        List<RideStop> orderedStops = rideStopRepository.findByRideIdOrderByStopOrder(ride.getId());
        List<com.carpool.dto.ride.RideStopDTO> stops = orderedStops.stream()
            .sorted(Comparator.comparingInt(RideStop::getStopOrder))
            .map(this::mapToRideStopDTO)
            .collect(Collectors.toList());

        List<com.carpool.dto.ride.RideSegmentDTO> segments = ride.getSegments().stream()
            .sorted(Comparator.comparingInt(RideSegment::getSegmentOrder))
            .map(this::mapToRideSegmentDTO)
            .collect(Collectors.toList());

        String routePreview = buildRoutePreview(orderedStops);

        return RideDetailsDTO.builder()
            .id(ride.getId())
            .ownerId(ride.getOwner().getId())
            .ownerName(ride.getOwner().getUser().getFullName())
            .ownerAverageRating(BigDecimal.ZERO) // TODO: Fetch from ratings
            .ownerRatingsCount(0L) // TODO: Fetch from ratings
            .date(ride.getDate())
            .carModel(ride.getCarModel())
            .totalSeats(ride.getTotalSeats())
            .availableSeats(ride.getAvailableSeats())
            .status(ride.getStatus())
            .pricingType(ride.getPricingType())
            .isMultiStop(ride.isMultiStop())
            .totalStops(ride.getTotalStops())
            .femaleOnly(ride.isFemaleOnly())
            .stops(stops)
            .segments(segments)
            .price(ride.getPrice())
            .cancellationReason(ride.getCancellationReason())
            .cancellationNote(ride.getCancellationNote())
            .cancelledAt(ride.getCancelledAt())
            .createdAt(ride.getCreatedAt())
            .updatedAt(ride.getUpdatedAt())
            .routePreview(routePreview)
            .build();
    }

    /**
     * Map RideStop to RideStopDTO.
     */
    private com.carpool.dto.ride.RideStopDTO mapToRideStopDTO(RideStop stop) {
        return com.carpool.dto.ride.RideStopDTO.builder()
            .stopOrder(stop.getStopOrder())
            .locationName(stop.getLocationName())
            .latitude(stop.getLatitude())
            .longitude(stop.getLongitude())
            .arrivalTime(stop.getArrivalTime())
            .departureTime(stop.getDepartureTime())
            .stopDurationMinutes(stop.getStopDurationMinutes())
            .build();
    }

    /**
     * Map RideSegment to RideSegmentDTO.
     */
    private com.carpool.dto.ride.RideSegmentDTO mapToRideSegmentDTO(RideSegment segment) {
        return com.carpool.dto.ride.RideSegmentDTO.builder()
            .id(segment.getId())
            .fromStopId(segment.getFromStop().getId())
            .toStopId(segment.getToStop().getId())
            .price(segment.getPrice())
            .availableSeats(segment.getAvailableSeats())
            .totalSeats(segment.getTotalSeats())
            .distanceKm(segment.getDistanceKm())
            .durationMinutes(segment.getDurationMinutes())
            .build();
    }

    // ========== RIDE MANAGEMENT ==========

    /**
     * Get a ride by ID (existing method, kept for compatibility).
     */
    public RideResponse get(UUID rideId) {
        return rideMapper.toResponse(findRide(rideId));
    }

    /**
     * List rides with filtering (existing method, kept for compatibility).
     */
    public Map<String, Object> list(String from, String to, LocalDate date, Integer passengers, RideStatus status, int page, int size, String sort) {
        RideStatus effective = status == null ? RideStatus.ACTIVE : status;
        Page<Ride> rides = rideRepository.findByStatusAndDateGreaterThanEqual(effective, LocalDate.now(),
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sort == null || sort.isBlank() ? "date" : sort)));

        String requesterGender = resolveRequesterGender();
        AppUserPrincipal principal = null;
        try { principal = authFacade.currentUser(); } catch (Exception ignored) {}
        boolean hideOwnerRidesForRequester = principal != null && principal.getRole() == Role.PASSENGER;
        java.util.UUID requesterUserId = principal == null ? null : principal.getUserId();
        java.util.UUID requesterOwnerId = principal == null ? null : principal.getOwnerId();
        String requesterMobile = principal == null ? null : principal.getMobile();

        List<RideResponse> filtered = rides.getContent().stream()
            .filter(r -> date == null || r.getDate().equals(date))
            .filter(r -> from == null || (r.getFromLocation() != null && r.getFromLocation().toLowerCase().contains(from.toLowerCase())))
            .filter(r -> to == null || (r.getToLocation() != null && r.getToLocation().toLowerCase().contains(to.toLowerCase())))
            .filter(r -> passengers == null || r.getTotalSeats() >= passengers)
            .filter(r -> r.getOwner().isVerified())
            .filter(r -> !r.isFemaleOnly() || requesterGender == null || "female".equalsIgnoreCase(requesterGender))
            .filter(r -> {
                if (!hideOwnerRidesForRequester) return true;
                try {
                    if (requesterOwnerId != null && requesterOwnerId.equals(r.getOwner().getId())) return false;
                    if (requesterUserId != null && r.getOwner().getUser() != null && requesterUserId.equals(r.getOwner().getUser().getId())) return false;
                    if (requesterMobile != null && r.getOwner().getMobile() != null && requesterMobile.equals(r.getOwner().getMobile())) return false;
                } catch (Exception ignored) {}
                return true;
            })
            .map(rideMapper::toResponse)
            .toList();

        return Map.of(
            "items", filtered,
            "meta", PageMeta.builder().page(page).size(size).totalElements(rides.getTotalElements()).totalPages(rides.getTotalPages()).build()
        );
    }

    /**
     * Find a ride and throw exception if not found.
     */
    private Ride findRide(UUID rideId) {
        return rideRepository.findById(rideId)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ride not found"));
    }

    /**
     * Resolve requester's gender from authentication.
     */
    private String resolveRequesterGender() {
        try {
            AppUserPrincipal principal = authFacade.currentUser();
            // TODO: Implement gender resolution from user profile
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Notify ride creation to owner and passengers.
     */
    private void notifyRideCreated(UUID ownerId, Ride ride) {
        try {
            notificationService.create(ownerId, com.carpool.entity.NotificationType.RIDE_CREATED,
                "Ride posted", "Your ride from " + getRideOrigin(ride) + " to " + getRideDestination(ride) + " was posted successfully.");
        } catch (Exception ignored) {}
    }

    /**
     * Get ride origin location.
     */
    private String getRideOrigin(Ride ride) {
        if (ride.isMultiStop() && !ride.getStops().isEmpty()) {
            return ride.getStops().get(0).getLocationName();
        }
        return ride.getFromLocation() != null ? ride.getFromLocation() : "Unknown";
    }

    private String getRideDestination(Ride ride) {
        if (ride.isMultiStop() && !ride.getStops().isEmpty()) {
            return ride.getStops().get(ride.getStops().size() - 1).getLocationName();
        }
        return ride.getToLocation() != null ? ride.getToLocation() : "Unknown";
    }
}
