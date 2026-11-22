package com.florin.tax;

import com.florin.common.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class Gstin {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Gstin() {}

    public static String parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String gstin = value.trim().toUpperCase(Locale.ROOT);
        if (!isValid(gstin)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid GSTIN, such as 27AAPFU0939F1ZV");
        }
        return gstin;
    }

    public static boolean isValid(String gstin) {
        return gstin != null
                && gstin.matches("[0-9]{2}[0-9A-Z]{13}")
                && GstStates.isState(gstin.substring(0, 2))
                && checkCharacter(gstin.substring(0, 14)) == gstin.charAt(14);
    }

    static char checkCharacter(String first14) {
        int sum = 0;
        for (int index = 0; index < 14; index++) {
            int product = ALPHABET.indexOf(first14.charAt(index)) * (index % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return ALPHABET.charAt((36 - sum % 36) % 36);
    }

    public static String stateCode(String gstin) {
        return gstin.substring(0, 2);
    }

    public static String pan(String gstin) {
        return gstin.substring(2, 12);
    }
}
