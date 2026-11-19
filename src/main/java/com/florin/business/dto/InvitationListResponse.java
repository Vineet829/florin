package com.florin.business.dto;

import java.util.List;

public record InvitationListResponse(boolean success, List<InvitationResponse> invitations) {}
