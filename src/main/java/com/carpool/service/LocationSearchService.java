package com.carpool.service;

import com.carpool.dto.location.LocationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationSearchService {
    private final OpenStreetMapService openStreetMapService;

    public List<LocationResponse> search(String query, String state) {
        return openStreetMapService.search(query, state).stream().map(place -> LocationResponse.builder()
            .osmId(place.osmId())
            .displayName(place.displayName())
            .latitude(place.latitude())
            .longitude(place.longitude())
            .boundingBox(place.boundingBox())
            .city(place.city())
            .district(place.district())
            .locality(place.locality())
            .street(place.street())
            .state(place.state())
            .country(place.country())
            .locationType(place.locationType())
            .geofenceRadius(place.geofenceRadius())
            .build()).toList();
    }
}