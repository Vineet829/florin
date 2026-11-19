package com.florin.auth.twofactor.dto;

import java.util.List;

public record RecoveryCodesResponse(boolean success, String message, List<String> recoveryCodes) {}
