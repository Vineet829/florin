package com.florin.common;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class Percentage {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private Percentage() {}

    public static BigDecimal parse(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (!text.matches("[0-9]{0,20}(\\.[0-9]{0,20})?")) {
            throw error(label);
        }
        try {
            BigDecimal number = new BigDecimal(text);
            if (number.signum() >= 0
                    && number.compareTo(ONE_HUNDRED) <= 0
                    && number.stripTrailingZeros().scale() <= 4) {
                return number;
            }
        } catch (NumberFormatException exception) {
            throw error(label);
        }
        throw error(label);
    }

    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static ApiException error(String label) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, label + " must be a number from 0 to 100, with up to 4 decimals");
    }
}
