package com.florin.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.florin.business.Business;
import com.florin.business.BusinessRole;
import com.florin.business.Permission;
import java.util.List;
import java.util.UUID;

public record BusinessSummaryResponse(
        @JsonProperty("_id") UUID id,
        String name,
        String logo,
        BusinessRole role,
        List<Permission> permissions,
        boolean active,
        long unread,
        String timeZone) {

    public static BusinessSummaryResponse from(
            Business business, BusinessRole role, boolean active, long unread, String timeZone) {
        return new BusinessSummaryResponse(
                business.getId(),
                business.getName(),
                business.getLogo(),
                role,
                List.copyOf(role.permissions()),
                active,
                unread,
                timeZone);
    }
}
