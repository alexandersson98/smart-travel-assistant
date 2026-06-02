package org.example;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.example.dto.RecommendationResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Snabba upp retry för tester
                "resilience4j.retry.instances.geoapify.wait-duration=50ms",
                "resilience4j.retry.instances.geoapify.max-attempts=2",
                "resilience4j.retry.instances.weather.wait-duration=50ms",
                "resilience4j.retry.instances.weather.max-attempts=2",
                // Liten CB för att kunna trigga OPEN snabbt
                "resilience4j.circuitbreaker.instances.geoapify.sliding-window-size=5",
                "resilience4j.circuitbreaker.instances.geoapify.minimum-number-of-calls=3"
        }
)
class RecommendationIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    static MockWebServer mockWeatherServer;
    static MockWebServer mockGeoapifyServer;

    @BeforeAll
    static void startServers() throws IOException {
        mockWeatherServer = new MockWebServer();
        mockWeatherServer.start();
        mockGeoapifyServer = new MockWebServer();
        mockGeoapifyServer.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        mockWeatherServer.shutdown();
        mockGeoapifyServer.shutdown();
    }

    @DynamicPropertySource
    static void configureUrls(DynamicPropertyRegistry registry) {
        registry.add("weather.api.base-url",
                () -> "http://localhost:" + mockWeatherServer.getPort());
        registry.add("geoapify.api.base-url",
                () -> "http://localhost:" + mockGeoapifyServer.getPort());
    }

    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.circuitBreaker("geoapify").reset();
        circuitBreakerRegistry.circuitBreaker("weather").reset();
    }

    // Test 1: Väder-fallback - väder-API misslyckas → "Sunny" används ändå
    @Test
    void weatherFallback_returnsSunnyWhenWeatherApiFails() {
        mockWeatherServer.enqueue(new MockResponse().setResponseCode(500));
        mockWeatherServer.enqueue(new MockResponse().setResponseCode(500));

        mockGeoapifyServer.enqueue(geocodeSuccess());
        mockGeoapifyServer.enqueue(placesSuccess());

        ResponseEntity<RecommendationResponse> response = restTemplate.getForEntity(
                "/api/recommendations?location=Stockholm", RecommendationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().weather()).isEqualTo("Sunny");
        assertThat(response.getBody().activities()).isNotEmpty();
    }

    // Test 2: Retry - Geoapify misslyckas första gången men lyckas på retry
    @Test
    void retry_succeedsAfterTransientFailure() {
        int countBefore = mockGeoapifyServer.getRequestCount();

        mockWeatherServer.enqueue(weatherSuccess());

        mockGeoapifyServer.enqueue(new MockResponse().setResponseCode(500));
        mockGeoapifyServer.enqueue(geocodeSuccess());
        mockGeoapifyServer.enqueue(placesSuccess());

        ResponseEntity<RecommendationResponse> response = restTemplate.getForEntity(
                "/api/recommendations?location=Stockholm", RecommendationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().activities()).isNotEmpty();
        // 1 misslyckat geocode + 1 lyckat geocode + 1 places = 3 anrop
        assertThat(mockGeoapifyServer.getRequestCount() - countBefore).isEqualTo(3);
    }

    // Test 3: Circuit Breaker övergår till OPEN efter tillräckligt många fel
    @Test
    void circuitBreaker_transitionsToOpenAfterFailures() {
        for (int i = 0; i < 3; i++) {
            mockWeatherServer.enqueue(weatherSuccess());
        }
        // 3 anrop × 2 försök = 6 felresponser
        for (int i = 0; i < 6; i++) {
            mockGeoapifyServer.enqueue(new MockResponse().setResponseCode(500));
        }

        for (int i = 0; i < 3; i++) {
            restTemplate.getForEntity(
                    "/api/recommendations?location=Stockholm", RecommendationResponse.class);
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geoapify");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // Test 4: Aktivitets-fallback returneras när Circuit Breaker är OPEN
    @Test
    void activityFallback_returnedWhenCircuitBreakerIsOpen() {
        int countBefore = mockGeoapifyServer.getRequestCount(); // spara före

        circuitBreakerRegistry.circuitBreaker("geoapify").transitionToOpenState();
        mockWeatherServer.enqueue(weatherSuccess());

        ResponseEntity<RecommendationResponse> response = restTemplate.getForEntity(
                "/api/recommendations?location=Stockholm", RecommendationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().activities()).isNotEmpty();
        assertThat(mockGeoapifyServer.getRequestCount()).isEqualTo(countBefore); // inga nya anrop
    }

    private MockResponse weatherSuccess() {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("{\"current\":{\"condition\":{\"text\":\"Sunny\"}}}");
    }

    private MockResponse geocodeSuccess() {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("{\"features\":[{\"properties\":{\"lat\":57.70,\"lon\":11.97}}]}");
    }

    private MockResponse placesSuccess() {
        return new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("{\"features\":[{\"properties\":{\"name\":\"Slottsskogen\"," +
                        "\"formatted\":\"Linnégatan, Göteborg\"," +
                        "\"categories\":[\"leisure.park\"]}}]}");
    }
}
