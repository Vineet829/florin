package com.florin.report;

import com.florin.business.BusinessAccess;
import com.florin.business.Permission;
import com.florin.business.Requires;
import com.florin.report.dto.CustomerSalesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerSalesController {

    private final CustomerSales customerSales;

    public CustomerSalesController(CustomerSales customerSales) {
        this.customerSales = customerSales;
    }

    @GetMapping("/sales")
    public CustomerSalesResponse sales(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "sort", defaultValue = "billed") String sort,
            @RequestParam(name = "dir", defaultValue = "desc") String direction,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return customerSales.list(access, period, currency, sort, direction, page, size);
    }
}
