package com.florin.settings.dto;

public record SettingsEnvelope(boolean success, String message, SettingsResponse settings) {}
