package org.example.service;

import org.example.dto.RecommendationResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class RecommendationService {

    private final WeatherService weatherService;
    private final GeoapifyService geoapifyService;

    public RecommendationService(WeatherService weatherService, GeoapifyService geoapifyService) {
        this.weatherService = weatherService;
        this.geoapifyService = geoapifyService;
    }

    public Mono<RecommendationResponse> getRecommendations(String city) {
        return weatherService.getWeatherCondition(city)
                .flatMap(weather ->
                        geoapifyService.getActivities(city, weather)
                                .map(activities -> new RecommendationResponse(city, weather, activities))
                );
    }
}