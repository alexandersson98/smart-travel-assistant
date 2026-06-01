package org.example;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import org.example.dto.WeatherResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class WeatherService {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final io.github.resilience4j.retry.Retry retry;

    @Value("${weather.api.key}")
    private String apiKey;

    public WeatherService(
            @Qualifier("weatherWebClient") WebClient webClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("weather");
        this.retry = retryRegistry.retry("weather");
    }

    public Mono<String> getWeatherCondition(String city) {
        return webClient.get()
                .uri("/current.json?key={key}&q={city}", apiKey, city)
                .retrieve()
                .bodyToMono(WeatherResponse.class)
                .map(r -> r.current().condition().text())
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(e -> Mono.just("Sunny"));
    }
}