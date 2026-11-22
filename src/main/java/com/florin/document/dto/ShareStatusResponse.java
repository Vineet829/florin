package com.florin.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record ShareStatusResponse(
        boolean success,
        boolean active,
        Instant createdAt,
        Instant lastViewedAt,
        int viewCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String url) {}
