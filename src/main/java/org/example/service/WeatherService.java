package org.example.service;

import org.example.dto.WeatherResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WeatherService {

    private final WebClient webClient;

    @Value("${weather.api.key}")
    private String apiKey;

    public WeatherService(@Qualifier("weatherWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getWeatherCondition(String city) {
        return webClient.get()
                .uri("/current.json?key={key}&q={city}", apiKey, city)
                .retrieve()
                .bodyToMono(WeatherResponse.class)
                .map(r -> r.current().condition().text());
    }
}