package org.example.controller;

import org.example.dto.RecommendationResponse;
import org.example.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendations")
    public Mono<ResponseEntity<RecommendationResponse>> getRecommendations(
            @RequestParam String location) {
        return recommendationService.getRecommendations(location)
                .map(ResponseEntity::ok);
    }
}