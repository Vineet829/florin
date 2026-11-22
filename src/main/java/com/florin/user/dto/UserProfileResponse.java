package com.florin.user.dto;

import com.florin.user.User;
import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
        String email,
        String username,
        String firstName,
        String lastName,
        boolean isEmailVerified,
        String provider,
        boolean hasPassword,
        boolean active,
        boolean demo,
        List<String> sharedBusinesses,
        Instant createdAt,
        Instant updatedAt) {

    public static UserProfileResponse from(User user, boolean demo, List<String> sharedBusinesses) {
        return new UserProfileResponse(
                user.getEmail(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.isEmailVerified(),
                user.getProvider(),
                user.getPassword() != null,
                user.isActive(),
                demo,
                sharedBusinesses,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
