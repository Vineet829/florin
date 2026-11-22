package com.florin.common;

public final class Pages {

    private Pages() {}

    public static int number(int requested, int size) {
        return Math.min(Math.max(requested, 1), Integer.MAX_VALUE / Math.max(size, 1));
    }
}
