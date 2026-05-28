package org.example.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {
    @Value("${weather.api.base-url}")
    private String weatherBaseUrl;

    @Value("${geoapify.api.base-url}")
    private String geoapifyBaseUrl;

    @Value("${geoapify.api.key}")
    private String geoapifyApiKey;

    @Value("${weather.api.timeout:5000}")
    private int weatherTimeout;

    @Value("${geoapify.api.timeout:5000}")
    private int geoapifyTimeout;



    @Bean("weatherWebClient")
    public WebClient weatherWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(weatherTimeout));

        return WebClient.builder()
                .baseUrl(weatherBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    @Bean("geoapifyWebClient")
    public WebClient geoapifyWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(geoapifyTimeout));

        return WebClient.builder()
                .baseUrl(geoapifyBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + geoapifyApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

