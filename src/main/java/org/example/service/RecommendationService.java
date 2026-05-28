package org.example.service;

import org.example.dto.RecommendationResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class RecommendationService {

    private final WeatherService weatherService;

    public RecommendationService(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    public Mono<RecommendationResponse> getRecommendations(String city) {
        return weatherService.getWeatherCondition(city)
                .map(weather -> new RecommendationResponse(
                        city,
                        weather,
                        List.of()
                ));
    }
}