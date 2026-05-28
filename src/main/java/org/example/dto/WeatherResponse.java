package org.example.dto;

public record WeatherResponse(Current current) {

    public record Current(Condition condition) {}

    public record Condition(String text) {}

}

