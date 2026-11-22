package com.florin.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyFormat {

    private MoneyFormat() {}

    public static String format(BigDecimal amount, String currency) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        String digits = value.abs().toPlainString();
        int point = digits.indexOf('.');
        String whole = grouped(digits.substring(0, point), "INR".equalsIgnoreCase(currency) ? 2 : 3);
        return (value.signum() < 0 ? "-" : "") + whole + digits.substring(point);
    }

    private static String grouped(String whole, int size) {
        StringBuilder out = new StringBuilder();
        int group = 3;
        int count = 0;
        for (int index = whole.length() - 1; index >= 0; index--) {
            if (count == group) {
                out.append(',');
                count = 0;
                group = size;
            }
            out.append(whole.charAt(index));
            count++;
        }
        return out.reverse().toString();
    }
}
