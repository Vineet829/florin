package com.florin.report;

import com.florin.business.BusinessAccess;
import com.florin.report.dto.PaymentListResponse;
import com.florin.report.dto.PaymentTotals;
import com.florin.settings.SettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentList {

    private final PaymentQueries queries;
    private final SettingsService settingsService;
    private final Clock clock;

    public PaymentList(PaymentQueries queries, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaymentListResponse list(BusinessAccess access, PaymentSearch search) {
        ZoneId zone = ZoneId.of(settingsService.forBusiness(access.businessPkid()).getTimeZone());
        PaymentSearch dated = search.on(LocalDate.now(clock.withZone(zone)));
        List<PaymentTotals> totals = queries.totals(access.businessPkid(), dated);
        long found = totals.stream().mapToLong(PaymentTotals::payments).sum();
        return new PaymentListResponse(
                true,
                found,
                (int) ((found + search.size() - 1) / search.size()),
                search.page(),
                search.size(),
                dated.from(),
                dated.to(),
                queries.page(access.businessPkid(), dated),
                totals,
                queries.methods(access.businessPkid()));
    }
}
