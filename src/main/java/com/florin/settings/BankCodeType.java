package com.florin.settings;

public enum BankCodeType {
    IFSC("IFSC"),
    SWIFT("SWIFT / BIC"),
    IBAN("IBAN"),
    SORT_CODE("Sort code"),
    ROUTING("Routing number"),
    BSB("BSB"),
    OTHER(null);

    private final String label;

    BankCodeType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
