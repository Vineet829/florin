package com.florin.document;

import com.florin.settings.BusinessSettings;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

final class ReminderPlanner {

    private ReminderPlanner() {}

    static boolean isDue(Document invoice, BusinessSettings settings, LocalDate today, ZoneId zone) {
        if (invoice.getDueDate() == null || invoice.getRemindersSent() >= settings.getReminderMax()) {
            return false;
        }
        LocalDate lastSent = invoice.getLastRemindedAt() == null
                ? null
                : LocalDate.ofInstant(invoice.getLastRemindedAt(), zone);
        if (today.equals(lastSent)) {
            return false;
        }
        return latestScheduled(LocalDate.ofInstant(invoice.getDueDate(), zone), settings, today)
                .filter(day -> invoice.getIssueDate() == null || !day.isBefore(invoice.getIssueDate()))
                .filter(day -> lastSent == null || day.isAfter(lastSent))
                .isPresent();
    }

    private static Optional<LocalDate> latestScheduled(LocalDate due, BusinessSettings settings, LocalDate today) {
        LocalDate latest = null;
        int before = settings.getReminderDaysBefore();
        if (before > 0 && !today.isBefore(due.minusDays(before)) && !today.isAfter(due)) {
            latest = due.minusDays(before);
        }
        if (settings.isReminderOnDue() && !today.isBefore(due)) {
            latest = due;
        }
        int every = settings.getReminderEveryDays();
        long late = ChronoUnit.DAYS.between(due, today);
        if (every > 0 && late >= every) {
            latest = due.plusDays(late - late % every);
        }
        return Optional.ofNullable(latest);
    }
}
