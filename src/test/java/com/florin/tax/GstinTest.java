package com.florin.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.florin.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GstinTest {

    @ParameterizedTest
    @ValueSource(strings = {"27AAPFU0939F1ZV", "29AAGCB7383J1Z4"})
    void acceptsValidGstins(String gstin) {
        assertThat(Gstin.isValid(gstin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "27AAPFU0939F1ZW",
        "27AAPFU0939F1Z",
        "99AAPFU0939F1ZV",
        "27aapfu0939f1zv",
        "27AAPFU0939F1Z-",
    })
    void refusesInvalidGstins(String gstin) {
        assertThat(Gstin.isValid(gstin)).isFalse();
    }

    @Test
    void checkCharacter() {
        assertThat(Gstin.checkCharacter("27AAPFU0939F1Z")).isEqualTo('V');
        assertThat(Gstin.checkCharacter("29AAGCB7383J1Z")).isEqualTo('4');
    }

    @Test
    void parseTrimsAndUppercases() {
        assertThat(Gstin.parse(" 27aapfu0939f1zv ")).isEqualTo("27AAPFU0939F1ZV");
        assertThat(Gstin.parse("  ")).isNull();
        assertThatThrownBy(() -> Gstin.parse("27AAPFU0939F1ZW"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Enter a valid GSTIN, such as 27AAPFU0939F1ZV");
    }

    @Test
    void readsStateAndPan() {
        assertThat(Gstin.stateCode("27AAPFU0939F1ZV")).isEqualTo("27");
        assertThat(Gstin.pan("27AAPFU0939F1ZV")).isEqualTo("AAPFU0939F");
    }

    @Test
    void namesStatesAndTerritories() {
        assertThat(GstStates.name("27")).isEqualTo("Maharashtra");
        assertThat(GstStates.name("96")).isEqualTo("Outside India");
        assertThat(GstStates.isState("96")).isFalse();
        assertThat(GstStates.isPlace("96")).isTrue();
        assertThat(GstStates.isUnionTerritoryWithoutLegislature("04")).isTrue();
        assertThat(GstStates.isUnionTerritoryWithoutLegislature("07")).isFalse();
        assertThat(GstStates.places()).last().extracting(GstStates.State::code).isEqualTo("96");
    }
}
