package com.carpool.dto.location;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LocationResponse {
    private UUID id;
    private String state;
    private String district;
    private String osmId;
    private String displayName;
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;
    private String boundingBox;
    private String city;
    private String locality;
    private String street;
    private String country;
    private String locationType;
    private Integer geofenceRadius;
}
