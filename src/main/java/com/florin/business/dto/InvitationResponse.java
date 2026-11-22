package com.florin.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.florin.business.BusinessRole;
import com.florin.business.Invitation;
import com.florin.user.User;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        @JsonProperty("_id") UUID id,
        String email,
        BusinessRole role,
        String invitedBy,
        Instant sentAt,
        Instant expiresAt,
        boolean expired) {

    public static InvitationResponse from(Invitation invitation, Instant now) {
        User inviter = invitation.getInvitedBy();
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getRole(),
                inviter == null ? null : (inviter.getFirstName() + " " + inviter.getLastName()).trim(),
                invitation.getSentAt(),
                invitation.getExpiresAt(),
                invitation.isExpiredAt(now));
    }
}
