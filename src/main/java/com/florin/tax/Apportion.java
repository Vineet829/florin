package com.florin.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Apportion {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Apportion() {}

    public static <K> Map<K, BigDecimal> of(Map<K, BigDecimal> values, BigDecimal factor, BigDecimal amount) {
        Map<K, BigDecimal> parts = new LinkedHashMap<>();
        BigDecimal given = ZERO;
        K largest = null;
        for (Map.Entry<K, BigDecimal> value : values.entrySet()) {
            BigDecimal part = amount.signum() == 0
                    ? ZERO
                    : value.getValue().multiply(factor).setScale(2, RoundingMode.HALF_UP);
            parts.put(value.getKey(), part);
            given = given.add(part);
            if (largest == null || value.getValue().compareTo(values.get(largest)) > 0) {
                largest = value.getKey();
            }
        }
        if (largest != null) {
            parts.merge(largest, amount.subtract(given), BigDecimal::add);
        }
        return parts;
    }
}
