package com.florin.business.dto;

public record BusinessEnvelope(boolean success, String message, BusinessProfileResponse business) {}
