package com.florin.document.dto;

import java.util.List;

public record RecurringListResponse(boolean success, List<RecurringResponse> schedules) {}
