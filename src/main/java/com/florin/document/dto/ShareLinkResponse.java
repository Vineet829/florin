package com.florin.document.dto;

import java.time.Instant;

public record ShareLinkResponse(boolean success, String url, Instant createdAt, boolean kept) {}
