package com.carpool.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OpenStreetMapService {
    private final RestClient client = RestClient.builder()
        .baseUrl("https://nominatim.openstreetmap.org")
        .defaultHeader("User-Agent", "CarShare247/1.0 location-search")
        .defaultHeader("Accept-Language", "en")
        .build();
    private final Map<String, CachedPlaces> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000;

    public synchronized List<Place> search(String query, String state) {
        String cacheKey = (query.trim().toLowerCase() + "|" + (state == null ? "" : state.trim().toLowerCase()));
        CachedPlaces cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return cached.places;

        List<Place> places = searchNominatim(query, state);
        cache.put(cacheKey, new CachedPlaces(places, System.currentTimeMillis() + CACHE_TTL_MILLIS));
        return places;
    }

    private List<Place> searchNominatim(String query, String state) {
        try {
            JsonNode response = client.get()
                .uri(uri -> uri.path("/search")
                    .queryParam("q", query + (state == null || state.isBlank() ? ", India" : ", " + state + ", India"))
                    .queryParam("format", "jsonv2")
                    .queryParam("accept-language", "en")
                    .queryParam("countrycodes", "in")
                    .queryParam("addressdetails", "1")
                    .queryParam("limit", "20")
                    .build())
                .retrieve().body(JsonNode.class);
            List<Place> places = new ArrayList<>();
            if (response != null && response.isArray()) {
                for (JsonNode item : response) {
                    if (!"in".equalsIgnoreCase(item.path("address").path("country_code").asText())) continue;
                    JsonNode address = item.path("address");
                    String district = first(address, "state_district", "district", "county");
                    String city = first(address, "city", "town", "municipality", "village");
                    String locality = first(address, "suburb", "neighbourhood", "quarter", "locality");
                    String street = first(address, "road", "pedestrian", "footway");
                    String providerState = first(address, "state");
                    places.add(new Place(
                        item.path("osm_id").asText(null),
                        item.path("display_name").asText(query + ", India"),
                        new BigDecimal(item.path("lat").asText()),
                        new BigDecimal(item.path("lon").asText()),
                        item.path("boundingbox").toString(),
                        city,
                        district,
                        locality,
                        street,
                        providerState,
                        "India",
                        item.path("type").asText("unknown").toUpperCase(),
                        1000));
                }
            }
            return places;
        } catch (Exception exception) {
            log.warn("Nominatim search failed for query {}", query, exception);
            return List.of();
        }
    }

    private String first(JsonNode node, String... names) {
        for (String name : names) if (node.hasNonNull(name)) return node.get(name).asText();
        return null;
    }

    private record CachedPlaces(List<Place> places, long expiresAt) {}

    public record Place(String osmId, String displayName, BigDecimal latitude, BigDecimal longitude,
                        String boundingBox, String city, String district, String locality, String street,
                        String state, String country,
                        String locationType, int geofenceRadius) {}
}