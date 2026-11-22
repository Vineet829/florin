package com.florin.report;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class AwayFromUtc implements ArgumentsProvider {

    @Override
    public Stream<Arguments> provideArguments(ExtensionContext context) {
        return Stream.of(
                        awayFromMidnight("Pacific/Kiritimati", "Pacific/Tongatapu"),
                        awayFromMidnight("Pacific/Pago_Pago", "Pacific/Honolulu"))
                .map(Arguments::of);
    }

    private static ZoneId awayFromMidnight(String zone, String otherwise) {
        ZoneId first = ZoneId.of(zone);
        return LocalTime.now(first).isBefore(LocalTime.of(23, 0)) ? first : ZoneId.of(otherwise);
    }
}
