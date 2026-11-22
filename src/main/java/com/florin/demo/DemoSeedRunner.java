package com.florin.demo;

import com.florin.config.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = "seed", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final DemoDataSeeder seeder;
    private final DemoReset reset;
    private final DemoProperties properties;
    private final TransactionTemplate transactionTemplate;

    public DemoSeedRunner(
            DemoDataSeeder seeder,
            DemoReset reset,
            DemoProperties properties,
            TransactionTemplate transactionTemplate) {
        this.seeder = seeder;
        this.reset = reset;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (properties.reset()) {
            renew();
            return;
        }
        attempt("Adding sample data to the demo account failed", this::seedDocuments);
        attempt("Adding sample expenses to the demo account failed", this::seedExpenses);
        attempt("Adding sample recurring invoices to the demo account failed", this::seedRecurring);
    }

    public void renew() {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                DemoReset.Result cleared = reset.clear(properties.email());
                if (cleared.cleared()) {
                    LOGGER.info("Cleared the demo account's business: {} records", cleared.records());
                } else {
                    LOGGER.info("Nothing cleared: {}", cleared.reason());
                }
                seedDocuments();
                seedExpenses();
                seedRecurring();
            });
        } catch (RuntimeException exception) {
            LOGGER.error("Renewing the demo account's sample data failed", exception);
        }
    }

    private void seedDocuments() {
        DemoDataSeeder.Result result = seeder.seed(properties.email());
        if (result.seeded()) {
            LOGGER.info(
                    "Added sample data to the demo account: {} clients, {} items, {} documents, {} payments",
                    result.clients(), result.items(), result.documents(), result.payments());
        } else {
            LOGGER.info("No sample data added: {}", result.reason());
        }
    }

    private void seedExpenses() {
        DemoDataSeeder.ExpenseResult result = seeder.seedExpenses(properties.email());
        if (result.seeded()) {
            LOGGER.info("Added sample expenses to the demo account: {} expenses", result.expenses());
        } else {
            LOGGER.info("No sample expenses added: {}", result.reason());
        }
    }

    private void seedRecurring() {
        DemoDataSeeder.RecurringResult result = seeder.seedRecurring(properties.email());
        if (result.seeded()) {
            LOGGER.info("Added sample recurring invoices to the demo account: {} schedules", result.schedules());
        } else {
            LOGGER.info("No sample recurring invoices added: {}", result.reason());
        }
    }

    private static void attempt(String failure, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            LOGGER.error(failure, exception);
        }
    }
}
