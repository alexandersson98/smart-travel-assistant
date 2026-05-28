package org.example.service;

import org.example.dto.GeoapifyGeoResponse;
import org.example.dto.GeoapifyPlacesResponse;
import org.example.dto.RecommendationResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GeoapifyService {
    @Value("${geoapify.api.key}")
    private String geoapifyApiKey;
    private final WebClient webClient;

    public GeoapifyService(@Qualifier("geoapifyWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<RecommendationResponse.Activity>> getActivities(String city, String weatherCondition) {
        String category = mapWeatherToCategory(weatherCondition);
        return geocodeCity(city)
                .flatMap(coords -> fetchPlaces(coords[0], coords[1], category));
    }

    private Mono<double[]> geocodeCity(String city) {
        return webClient.get()
                .uri("/v1/geocode/search?text={city}&limit=1&apiKey={key}", city, geoapifyApiKey)
                .retrieve()
                .bodyToMono(GeoapifyGeoResponse.class)
                .map(response -> {
                    if (response.features() == null || response.features().isEmpty()) {
                        throw new RuntimeException("Ingen geocoding-träff för: " + city);
                    }
                    var props = response.features().get(0).properties();
                    return new double[]{props.lat(), props.lon()};
                });
    }





    private Mono<List<RecommendationResponse.Activity>> fetchPlaces(double lat, double lon, String category) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/places")
                        .queryParam("categories", category)
                        .queryParam("filter", "circle:" + lon + "," + lat + ",5000")
                        .queryParam("limit", 5)
                        .queryParam("apiKey", geoapifyApiKey)
                        .build())
                .retrieve()
                .bodyToMono(GeoapifyPlacesResponse.class)
                .map(this::mapToActivities);
    }

    private List<RecommendationResponse.Activity> mapToActivities(GeoapifyPlacesResponse response) {
        if (response.features() == null || response.features().isEmpty()) {
            return List.of();
        }
        return response.features().stream()
                .map(f -> new RecommendationResponse.Activity(
                        f.properties().name() != null ? f.properties().name() : "Okänd plats",
                        f.properties().formatted() != null ? f.properties().formatted() : "Adress saknas",
                        f.properties().categories() != null && !f.properties().categories().isEmpty()
                                ? f.properties().categories().get(0) : "general"
                ))
                .toList();
    }

    private String mapWeatherToCategory(String condition) {
        if (condition == null) return "tourism.attraction";
        String lower = condition.toLowerCase();
        if (lower.contains("rain") || lower.contains("snow") || lower.contains("sleet")
                || lower.contains("fog") || lower.contains("mist") || lower.contains("thunder")
                || lower.contains("overcast") || lower.contains("cloudy")) {
            return "entertainment.museum";
        }
        return "leisure.park";
    }
}