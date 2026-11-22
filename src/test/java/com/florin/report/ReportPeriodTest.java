package com.florin.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.florin.common.ApiException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    @Test
    void lastMonths() {
        LocalDate today = LocalDate.of(2026, 10, 8);
        assertThat(ReportPeriod.of(null, today))
                .isEqualTo(new ReportPeriod("12m", LocalDate.of(2025, 10, 9), today));
        assertThat(ReportPeriod.of("6M", today)).isEqualTo(new ReportPeriod("6m", LocalDate.of(2026, 4, 9), today));
        assertThat(ReportPeriod.of("3m", today)).isEqualTo(new ReportPeriod("3m", LocalDate.of(2026, 7, 9), today));
        assertThat(ReportPeriod.of("3m", today).months()).containsExactly(
                YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        assertThat(ReportPeriod.of("6m", today).months()).hasSize(7);
        assertThat(ReportPeriod.of("12m", today).months()).hasSize(13);
        for (LocalDate day : new LocalDate[] {today, TODAY, LocalDate.of(2028, 2, 29), LocalDate.of(2027, 1, 1)}) {
            OverviewWindow window = OverviewWindow.of("12m", day);
            assertThat(ReportPeriod.of("12m", day)).isEqualTo(new ReportPeriod("12m", window.from(), window.to()));
        }
    }

    @Test
    void monthEnds() {
        LocalDate march = LocalDate.of(2026, 3, 31);
        assertThat(ReportPeriod.of("3m", march).from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(ReportPeriod.of("6m", march).from()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(ReportPeriod.of("12m", march).from()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(ReportPeriod.of("3m", march).months()).containsExactly(
                YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3));
        assertThat(ReportPeriod.of("3m", LocalDate.of(2026, 5, 31)).from()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(ReportPeriod.of("3m", LocalDate.of(2026, 5, 29)).from()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(ReportPeriod.of("12m", TODAY).from()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(ReportPeriod.of("6m", TODAY).from()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(ReportPeriod.of("6m", TODAY).months()).hasSize(6);
        assertThat(ReportPeriod.of("3m", TODAY).from()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(ReportPeriod.of("3m", LocalDate.of(2026, 5, 30)).from()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(ReportPeriod.of("12m", LocalDate.of(2028, 2, 29)).from()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void lastDays() {
        LocalDate today = LocalDate.of(2026, 10, 4);
        assertThat(ReportPeriod.of("30d", today))
                .isEqualTo(new ReportPeriod("30d", LocalDate.of(2026, 9, 5), today));
        assertThat(ReportPeriod.of("90D", today))
                .isEqualTo(new ReportPeriod("90d", LocalDate.of(2026, 7, 7), today));
        assertThat(ReportPeriod.of("30d", today).months()).containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        assertThat(ReportPeriod.of("90d", today).months()).containsExactly(
                YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        assertThat(ReportPeriod.of("30d", TODAY).months()).containsExactly(YearMonth.of(2026, 9));
        for (String key : new String[] {"30d", "90d"}) {
            for (LocalDate day : new LocalDate[] {today, TODAY, LocalDate.of(2028, 2, 29), LocalDate.of(2027, 1, 1)}) {
                OverviewWindow window = OverviewWindow.of(key, day);
                assertThat(ReportPeriod.of(key, day)).isEqualTo(new ReportPeriod(key, window.from(), window.to()));
            }
        }
    }

    @Test
    void thisAndEarlierYears() {
        assertThat(ReportPeriod.of("ytd", TODAY)).isEqualTo(new ReportPeriod("ytd", LocalDate.of(2026, 1, 1), TODAY));
        assertThat(ReportPeriod.of("2025", TODAY))
                .isEqualTo(new ReportPeriod("2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        assertThat(ReportPeriod.of("2025", TODAY).months()).hasSize(12);
    }

    @Test
    void daysOfAMonth() {
        LocalDate from = LocalDate.of(2026, 9, 8);
        LocalDate to = LocalDate.of(2026, 10, 7);
        assertThat(ReportPeriod.firstDay(YearMonth.of(2026, 9), from)).isEqualTo(from);
        assertThat(ReportPeriod.lastDay(YearMonth.of(2026, 9), to)).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(ReportPeriod.firstDay(YearMonth.of(2026, 10), from)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ReportPeriod.lastDay(YearMonth.of(2026, 10), to)).isEqualTo(to);
        assertThat(ReportPeriod.firstDay(YearMonth.of(2025, 2), LocalDate.of(2025, 1, 1)))
                .isEqualTo(LocalDate.of(2025, 2, 1));
        assertThat(ReportPeriod.lastDay(YearMonth.of(2025, 2), LocalDate.of(2025, 12, 31)))
                .isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026", "2027", "1999", "+2025", "7d", "60d", "all"})
    void refusesUnknownPeriods(String key) {
        assertThatThrownBy(() -> ReportPeriod.of(key, TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessage("Choose a period from the list");
    }
}
