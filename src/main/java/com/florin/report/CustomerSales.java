package com.florin.report;

import com.florin.business.BusinessAccess;
import com.florin.common.ApiException;
import com.florin.report.dto.ClientTotals;
import com.florin.report.dto.CustomerSalesResponse;
import com.florin.settings.BusinessSettings;
import com.florin.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerSales {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, Function<CustomerSalesResponse.Client, BigDecimal>> AMOUNTS = Map.of(
            "billed", CustomerSalesResponse.Client::billed,
            "paid", CustomerSalesResponse.Client::paid,
            "owed", CustomerSalesResponse.Client::owed);

    private final ReportQueries queries;
    private final BilledCurrencies billedCurrencies;
    private final SettingsService settingsService;
    private final Clock clock;

    public CustomerSales(
            ReportQueries queries, BilledCurrencies billedCurrencies, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.billedCurrencies = billedCurrencies;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CustomerSalesResponse list(
            BusinessAccess access,
            String periodKey,
            String requested,
            String sort,
            String direction,
            int pageNumber,
            int pageSize) {
        Comparator<CustomerSalesResponse.Client> order = order(sort, "asc".equalsIgnoreCase(direction));
        int page = Math.max(pageNumber, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(settings.getTimeZone())));
        ListPeriod period = ListPeriod.of(periodKey, today);
        List<String> currencies = billedCurrencies.list(businessPkid, settings.getDefaultCurrency());
        String currency = BilledCurrencies.chosen(currencies, requested, settings.getDefaultCurrency());
        List<CustomerSalesResponse.Client> clients =
                queries.clients(businessPkid, period.firstDay(), period.lastDay(), ReportQueries.View.OWN).stream()
                        .filter(client -> client.currency().equals(currency))
                        .map(ReportService::clientTotals)
                        .map(CustomerSales::client)
                        .sorted(order)
                        .toList();
        return new CustomerSalesResponse(
                true,
                currency,
                currencies,
                period.view(),
                clients.size(),
                page,
                size,
                (clients.size() + size - 1) / size,
                new CustomerSalesResponse.Totals(
                        clients.stream().mapToLong(CustomerSalesResponse.Client::documents).sum(),
                        sum(clients, CustomerSalesResponse.Client::billed),
                        sum(clients, CustomerSalesResponse.Client::paid),
                        sum(clients, CustomerSalesResponse.Client::owed)),
                clients.stream().skip((long) (page - 1) * size).limit(size).toList());
    }

    private static CustomerSalesResponse.Client client(ClientTotals client) {
        return new CustomerSalesResponse.Client(
                client.customerId(),
                client.name(),
                client.documents(),
                client.billed(),
                client.paid(),
                client.outstanding());
    }

    private static Comparator<CustomerSalesResponse.Client> order(String sort, boolean ascending) {
        Comparator<CustomerSalesResponse.Client> byName = Comparator.<CustomerSalesResponse.Client, String>comparing(
                        client -> name(client).toLowerCase(Locale.ROOT))
                .thenComparing(CustomerSales::name)
                .thenComparing(
                        client -> client.clientId() == null ? null : client.clientId().toString(),
                        Comparator.nullsLast(Comparator.naturalOrder()));
        if ("name".equals(sort)) {
            return ascending ? byName : byName.reversed();
        }
        Function<CustomerSalesResponse.Client, BigDecimal> amount = AMOUNTS.get(sort == null ? "billed" : sort);
        if (amount == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Clients can be sorted by billed, paid, owed or name");
        }
        Comparator<BigDecimal> direction = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(amount, direction).thenComparing(byName);
    }

    private static String name(CustomerSalesResponse.Client client) {
        return Objects.requireNonNullElse(client.client(), "");
    }

    private static BigDecimal sum(
            List<CustomerSalesResponse.Client> clients, Function<CustomerSalesResponse.Client, BigDecimal> amount) {
        return clients.stream().map(amount).reduce(ZERO, BigDecimal::add);
    }
}
