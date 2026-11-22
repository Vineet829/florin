package com.florin.tax;

public enum SupplyType {
    INTRA,
    INTER,
    EXPORT_LUT,
    EXPORT_IGST;

    public boolean export() {
        return this == EXPORT_LUT || this == EXPORT_IGST;
    }
}
