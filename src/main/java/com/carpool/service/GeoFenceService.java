package com.carpool.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class GeoFenceService {
    private static final double EARTH_RADIUS_KM = 6371.0088d;

    public boolean contains(BigDecimal centerLatitude, BigDecimal centerLongitude,
                            BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        if (centerLatitude == null || centerLongitude == null || latitude == null || longitude == null) return false;
        return distanceMeters(centerLatitude, centerLongitude, latitude, longitude) <= Math.max(0, radiusMeters);
    }

    public double distanceMeters(BigDecimal firstLatitude, BigDecimal firstLongitude,
                                 BigDecimal secondLatitude, BigDecimal secondLongitude) {
        double firstLat = Math.toRadians(firstLatitude.doubleValue());
        double secondLat = Math.toRadians(secondLatitude.doubleValue());
        double deltaLat = secondLat - firstLat;
        double deltaLon = Math.toRadians(secondLongitude.doubleValue() - firstLongitude.doubleValue());
        double haversine = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(firstLat) * Math.cos(secondLat)
            * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine)) * 1000;
    }
}