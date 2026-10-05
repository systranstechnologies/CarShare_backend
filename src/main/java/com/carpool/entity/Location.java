package com.carpool.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "locations")
public class Location extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "char(36)")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 150)
    private String district;

    @Column(length = 100)
    private String osmId;

    @Column(length = 500)
    private String displayName;

    @Column(precision = 10, scale = 8)
    private java.math.BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private java.math.BigDecimal longitude;

    @Column(columnDefinition = "text")
    private String boundingBox;

    @Column(length = 200)
    private String city;

    @Column(length = 200)
    private String locality;

    @Column(length = 200)
    private String street;

    @Column(length = 200)
    private String country;

    @Column(length = 50)
    private String locationType;

    @Column(nullable = false)
    private Integer geofenceRadius = 1000;
}
