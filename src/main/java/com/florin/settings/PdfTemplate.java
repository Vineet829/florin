package com.florin.settings;

public enum PdfTemplate {
    CLASSIC(null),
    MODERN("modern"),
    COMPACT("compact");

    private final String theme;

    PdfTemplate(String theme) {
        this.theme = theme;
    }

    public String theme() {
        return theme;
    }
}
