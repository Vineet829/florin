package com.florin.business.dto;

import com.florin.business.BusinessRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "Choose a role") BusinessRole role) {}
