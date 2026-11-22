package com.florin.business;

import com.florin.common.ApiException;
import com.florin.user.User;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record BusinessAccess(Long businessPkid, UUID businessId, BusinessRole role, User user) {

    public Long userPkid() {
        return user.getPkid();
    }

    public boolean can(Permission permission) {
        return role.allows(permission);
    }

    public void require(Permission permission) {
        if (!can(permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN, permission.refusal());
        }
    }
}
