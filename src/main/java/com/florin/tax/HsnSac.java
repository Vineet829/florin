package com.florin.tax;

import com.florin.common.ApiException;
import org.springframework.http.HttpStatus;

public final class HsnSac {

    private HsnSac() {}

    public static String parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.replaceAll("\\s+", "");
        if (!code.matches("[0-9]{4,8}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter an HSN or SAC code of 4 to 8 digits");
        }
        return code;
    }
}
