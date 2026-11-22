package com.florin.business.dto;

import com.florin.business.BusinessRole;
import java.time.Instant;

public record InvitationPreviewResponse(
        boolean success,
        String businessName,
        BusinessRole role,
        String email,
        String invitedBy,
        Instant expiresAt,
        String status) {}
