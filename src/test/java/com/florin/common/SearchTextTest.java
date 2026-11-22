package com.florin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTextTest {

    @Test
    void matchesAnywhereIgnoringCase() {
        assertThat(SearchText.containing("  Acme Ltd ")).isEqualTo("%acme ltd%");
        assertThat(SearchText.containing(null)).isEqualTo("%%");
        assertThat(SearchText.containing(" ")).isEqualTo("%%");
    }

    @Test
    void escapesWildcards() {
        assertThat(SearchText.containing("50%_off!")).isEqualTo("%50!%!_off!!%");
    }
}
