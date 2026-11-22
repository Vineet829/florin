package com.florin.document.dto;

import jakarta.validation.constraints.Size;

public record DeclineRequest(@Size(max = 500, message = "The reason can be at most 500 characters") String reason) {}
