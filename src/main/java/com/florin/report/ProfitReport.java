package com.florin.report;

import com.florin.business.BusinessAccess;
import com.florin.report.dto.ProfitCategory;
import com.florin.report.dto.ProfitCurrency;
import com.florin.report.dto.ProfitMonth;
import com.florin.report.dto.ProfitReportResponse;
import com.florin.settings.BusinessSettings;
import com.florin.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfitReport {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ReportQueries queries;
    private final SettingsService settingsService;
    private final Clock clock;

    public ProfitReport(ReportQueries queries, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProfitReportResponse report(BusinessAccess access, String periodKey) {
        BusinessSettings settings = settingsService.forBusiness(access.businessPkid());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(settings.getTimeZone())));
        ReportPeriod period = ReportPeriod.of(periodKey, today);
        List<ReportQueries.Earned> earned = queries.earned(access.businessPkid(), period.from(), period.to());
        List<ReportQueries.Spent> spent = queries.spent(access.businessPkid(), period.from(), period.to());
        List<ReportQueries.CategorySpent> categories =
                queries.spentByCategory(access.businessPkid(), period.from(), period.to());
        TreeSet<String> currencies = new TreeSet<>();
        earned.forEach(row -> currencies.add(row.currency()));
        spent.forEach(row -> currencies.add(row.currency()));
        return new ProfitReportResponse(
                true,
                period.key(),
                period.from(),
                period.to(),
                ReportService.inOrder(currencies, settings.getDefaultCurrency()).stream()
                        .map(currency -> currency(currency, earned, spent, categories))
                        .toList());
    }

    private static ProfitCurrency currency(
            String currency,
            List<ReportQueries.Earned> earned,
            List<ReportQueries.Spent> spent,
            List<ReportQueries.CategorySpent> categories) {
        Map<YearMonth, BigDecimal> income = new HashMap<>();
        earned.stream()
                .filter(row -> row.currency().equals(currency))
                .forEach(row -> income.merge(YearMonth.from(row.month()), row.income(), BigDecimal::add));
        Map<YearMonth, BigDecimal> costs = new HashMap<>();
        spent.stream()
                .filter(row -> row.currency().equals(currency))
                .forEach(row -> costs.merge(YearMonth.from(row.month()), row.cost(), BigDecimal::add));
        TreeSet<YearMonth> active = new TreeSet<>(income.keySet());
        active.addAll(costs.keySet());
        List<ProfitMonth> months = active.stream()
                .map(month -> {
                    BigDecimal in = income.getOrDefault(month, ZERO);
                    BigDecimal out = costs.getOrDefault(month, ZERO);
                    return new ProfitMonth(month, in, out, in.subtract(out));
                })
                .toList();
        BigDecimal totalIncome = sum(months, ProfitMonth::income);
        BigDecimal totalExpenses = sum(months, ProfitMonth::expenses);
        return new ProfitCurrency(
                currency,
                totalIncome,
                totalExpenses,
                totalIncome.subtract(totalExpenses),
                months,
                categories.stream()
                        .filter(row -> row.currency().equals(currency))
                        .map(row -> new ProfitCategory(row.category(), row.amount()))
                        .sorted(largestFirst(ProfitCategory::expenses, ProfitCategory::category))
                        .toList());
    }

    static <T> Comparator<T> largestFirst(Function<T, BigDecimal> amount, Function<T, String> name) {
        return Comparator.comparing(amount, Comparator.<BigDecimal>reverseOrder())
                .thenComparing(row -> name.apply(row).toLowerCase(Locale.ROOT))
                .thenComparing(name);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> amount) {
        return rows.stream().map(amount).reduce(ZERO, BigDecimal::add);
    }
}
