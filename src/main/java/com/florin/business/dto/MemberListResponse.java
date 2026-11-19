package com.florin.business.dto;

import java.util.List;

public record MemberListResponse(boolean success, List<MemberResponse> members) {}
