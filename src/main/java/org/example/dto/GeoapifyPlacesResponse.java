package org.example.dto;

import java.util.List;

public record GeoapifyPlacesResponse(List<PlaceFeature> features) {

    public record PlaceFeature(PlaceProperties properties) {}

    public record PlaceProperties(String name, String formatted, List<String> categories) {}
}