package com.florin.business;

import java.util.EnumSet;
import java.util.Set;

public enum BusinessRole {
    OWNER(EnumSet.allOf(Permission.class)),
    ADMIN(EnumSet.complementOf(EnumSet.of(Permission.OWN_BUSINESS))),
    MEMBER(EnumSet.of(Permission.VIEW_RECORDS, Permission.EXPORT_LISTS, Permission.EDIT_RECORDS)),
    VIEWER(EnumSet.of(Permission.VIEW_RECORDS, Permission.EXPORT_LISTS));

    private final Set<Permission> permissions;

    BusinessRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean allows(Permission permission) {
        return permissions.contains(permission);
    }

    public Set<Permission> permissions() {
        return EnumSet.copyOf(permissions);
    }
}
