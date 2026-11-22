package com.florin.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceholdersTest {

    private static final Map<String, String> VALUES =
            Map.of("client", "Morrow & Co.", "document", "Invoice INV-0042", "business", "Apte Studio");

    @Test
    void fillsEveryOccurrence() {
        String message = "Hello {client},\n\nPlease find {document} from {business}.\n\n{business}";

        assertThat(Placeholders.fill(message, VALUES))
                .isEqualTo("Hello Morrow & Co.,\n\nPlease find Invoice INV-0042 from Apte Studio.\n\nApte Studio");
    }

    @Test
    void insertsValuesVerbatim() {
        Map<String, String> lookAlike = Map.of("client", "{business}", "business", "Apte");

        assertThat(Placeholders.fill("Hello {client}, from {business}", lookAlike))
                .isEqualTo("Hello {business}, from Apte");
        assertThat(Placeholders.fill("Hello {client}", Map.of("client", "Cash $1 \\ Co")))
                .isEqualTo("Hello Cash $1 \\ Co");
    }

    @Test
    void keepsUnfilledPlaceholders() {
        assertThat(Placeholders.fill("Due {due}, {Client}, { client }, {client", VALUES))
                .isEqualTo("Due {due}, {Client}, { client }, {client");
    }

    @Test
    void dropsEmptyLines() {
        String message = "Hello {client},\n\nView it online: {link}\n\n{payment}\n\n{business}";
        Map<String, String> linked = Map.of("client", "Neha", "link", "https://x", "payment", "", "business", "Bo");

        assertThat(Placeholders.fill(message, Map.of("client", "Neha", "link", "", "payment", "", "business", "Bo")))
                .isEqualTo("Hello Neha,\n\nBo");
        assertThat(Placeholders.fill(message, linked)).isEqualTo("Hello Neha,\n\nView it online: https://x\n\nBo");
        assertThat(Placeholders.fill("{link} or {business}", Map.of("link", "", "business", "Bo")))
                .isEqualTo(" or Bo");
        assertThat(Placeholders.fill("\nHi\n\n\n{due}", Map.of())).isEqualTo("\nHi\n\n\n{due}");
    }
}
