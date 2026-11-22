package com.florin.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurringScheduleTest {

    private static final LocalDate JANUARY_31 = LocalDate.of(2026, 1, 31);

    private static RecurringSchedule movedToJanuary(Integer maxRuns, LocalDate endDate) {
        RecurringSchedule schedule = new RecurringSchedule();
        schedule.setFrequency(RecurringFrequency.MONTHLY);
        schedule.setStartDate(JANUARY_31);
        schedule.setStartOffset(1);
        schedule.setRunsDone(2);
        schedule.setMaxRuns(maxRuns);
        schedule.setEndDate(endDate);
        return schedule;
    }

    @Test
    void monthEnds() {
        RecurringSchedule schedule = movedToJanuary(null, null);

        assertThat(schedule.runAfter(1)).isEqualTo(JANUARY_31);
        assertThat(schedule.runAfter(2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(schedule.runAfter(3)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(schedule.runAfter(4)).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void noRunPastLimits() {
        RecurringSchedule most = movedToJanuary(3, null);
        assertThat(most.runAfter(2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(most.runAfter(3)).isNull();

        RecurringSchedule ending = movedToJanuary(null, LocalDate.of(2026, 3, 15));
        assertThat(ending.runAfter(2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(ending.runAfter(3)).isNull();
    }
}
