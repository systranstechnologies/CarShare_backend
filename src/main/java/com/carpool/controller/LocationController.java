package com.carpool.controller;

import com.carpool.dto.ApiResponse;
import com.carpool.dto.location.LocationResponse;
import com.carpool.repository.LocationRepository;
import com.carpool.service.LocationSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationRepository locationRepository;
    private final LocationSearchService locationSearchService;

    @GetMapping("/search")
    public ApiResponse<?> geoSearch(@RequestParam String query, @RequestParam(required = false) String state) {
        return ApiResponse.of(locationSearchService.search(query.trim(), state));
    }

    @GetMapping
    public ApiResponse<?> search(@RequestParam(required = false) String query, @RequestParam(required = false) String state) {
        String q = query == null ? "" : query.trim();
        String s = state == null ? "" : state.trim();
        List<?> items;
        if (!s.isEmpty()) {
            items = locationRepository.findByStateIgnoreCaseContainingAndDistrictIgnoreCaseContaining(s, q).stream()
                .map(this::toResponse).collect(Collectors.toList());
        } else {
            items = locationRepository.findByDistrictIgnoreCaseContaining(q).stream()
                .map(this::toResponse).collect(Collectors.toList());
        }
        return ApiResponse.of(items);
    }

    private LocationResponse toResponse(com.carpool.entity.Location location) {
        return LocationResponse.builder().id(location.getId()).state(location.getState()).district(location.getDistrict())
            .osmId(location.getOsmId()).displayName(location.getDisplayName()).latitude(location.getLatitude())
            .longitude(location.getLongitude()).boundingBox(location.getBoundingBox()).city(location.getCity())
            .country(location.getCountry()).locationType(location.getLocationType()).geofenceRadius(location.getGeofenceRadius()).build();
    }
}
