package org.example.dto;

import java.util.List;

public record RecommendationResponse(String city, String weather, List<Activity>activities) {
    public record Activity (String name, String address, String category){}
}
