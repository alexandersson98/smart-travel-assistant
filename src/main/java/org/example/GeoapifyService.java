package org.example;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
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

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final io.github.resilience4j.retry.Retry retry;

    @Value("${geoapify.api.key}")
    private String geoapifyApiKey;

    public GeoapifyService(
            @Qualifier("geoapifyWebClient") WebClient webClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("geoapify");
        this.retry = retryRegistry.retry("geoapify");
    }

    public Mono<List<RecommendationResponse.Activity>> getActivities(String city, String weatherCondition) {
        String category = mapWeatherToCategory(weatherCondition);
        return geocodeCity(city)
                .flatMap(coords -> fetchPlaces(coords[0], coords[1], category))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(e -> Mono.just(getFallbackList()));
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
            return getFallbackList();
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

    private List<RecommendationResponse.Activity> getFallbackList() {
        return List.of(
                new RecommendationResponse.Activity("Universeum", "Södra Vägen 50, Göteborg", "entertainment.museum"),
                new RecommendationResponse.Activity("Slottsskogen", "Linnégatan, Göteborg", "leisure.park"),
                new RecommendationResponse.Activity("Göteborgs Konstmuseum", "Götaplatsen, Göteborg", "entertainment.museum")
        );
    }
}