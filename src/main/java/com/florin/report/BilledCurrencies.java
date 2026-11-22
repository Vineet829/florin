package com.florin.report;

import com.florin.document.DocumentLedger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class BilledCurrencies {

    private final JdbcClient jdbc;

    BilledCurrencies(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<String> list(Long businessPkid, String defaultCurrency) {
        List<String> found = jdbc.sql("""
                        select distinct coalesce(d.currency, '') as currency from documents d
                        where d.business_pkid = :business and %s and d.document_type <> 'Quotation'
                        order by 1
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .query(String.class)
                .list();
        List<String> currencies = new ArrayList<>();
        if (found.contains(defaultCurrency)) {
            currencies.add(defaultCurrency);
        }
        found.stream().filter(currency -> !currency.equals(defaultCurrency)).forEach(currencies::add);
        return currencies;
    }

    static String chosen(List<String> currencies, String requested, String defaultCurrency) {
        return currencies.contains(requested)
                ? requested
                : currencies.isEmpty() ? defaultCurrency : currencies.get(0);
    }
}
