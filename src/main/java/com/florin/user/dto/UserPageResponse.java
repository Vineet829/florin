package com.florin.user.dto;

import java.util.List;

public record UserPageResponse(
        boolean success, long count, int numberOfPages, List<UserSummaryResponse> users) {}
