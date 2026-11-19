package com.florin.business.dto;

import java.util.List;

public record BusinessListResponse(boolean success, List<BusinessSummaryResponse> businesses) {}
