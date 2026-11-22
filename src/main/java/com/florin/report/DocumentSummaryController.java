package com.florin.report;

import com.florin.business.BusinessAccess;
import com.florin.business.Permission;
import com.florin.business.Requires;
import com.florin.report.dto.DocumentSummaryResponse;
import com.florin.report.dto.ReceivablesResponse;
import com.florin.report.dto.SalesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document")
public class DocumentSummaryController {

    private final Overview overview;
    private final Receivables receivables;
    private final Sales sales;

    public DocumentSummaryController(Overview overview, Receivables receivables, Sales sales) {
        this.overview = overview;
        this.receivables = receivables;
        this.sales = sales;
    }

    @GetMapping("/summary")
    public DocumentSummaryResponse summary(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "currency", required = false) String currency) {
        return overview.documentSummary(access, currency);
    }

    @GetMapping("/receivables")
    public ReceivablesResponse receivables(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "currency", required = false) String currency) {
        return receivables.summary(access, currency);
    }

    @GetMapping("/sales")
    public SalesResponse sales(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "currency", required = false) String currency) {
        return sales.summary(access, period, currency);
    }
}
