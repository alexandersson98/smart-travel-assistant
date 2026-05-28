package org.example.dto;

import java.util.List;

public record GeoapifyGeoResponse(List<GeoFeature> features) {

    public record GeoFeature(GeoProperties properties) {
    }

    public record GeoProperties(double lat, double lon) {
    }
}
