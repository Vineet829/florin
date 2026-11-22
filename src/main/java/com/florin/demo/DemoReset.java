package com.florin.demo;

import com.florin.business.BusinessService;
import com.florin.user.User;
import com.florin.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DemoReset {

    private static final String DOCUMENTS = "(select pkid from documents where business_pkid = ?)";
    private static final List<String> STATEMENTS = List.of(
            "delete from recurring_runs where schedule_pkid in"
                    + " (select pkid from recurring_schedules where business_pkid = ?)",
            "delete from recurring_schedules where business_pkid = ?",
            "delete from payment_claims where business_pkid = ?",
            "delete from notifications where business_pkid = ?",
            "delete from document_events where document_pkid in " + DOCUMENTS,
            "delete from share_links where document_pkid in " + DOCUMENTS,
            "delete from refunds where document_pkid in " + DOCUMENTS,
            "delete from payments where document_pkid in " + DOCUMENTS,
            "delete from document_tax_summaries where document_pkid in " + DOCUMENTS,
            "delete from document_billing_items where document_pkid in " + DOCUMENTS,
            "delete from documents where business_pkid = ?",
            "delete from portal_links where business_pkid = ?",
            "delete from customers where business_pkid = ?",
            "delete from catalog_items where business_pkid = ?",
            "delete from expenses where business_pkid = ?",
            "delete from email_templates where business_pkid = ?",
            "delete from business_settings where business_pkid = ?");

    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public DemoReset(
            UserRepository userRepository,
            BusinessService businessService,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public record Result(boolean cleared, String reason, int records) {

        static Result skipped(String reason) {
            return new Result(false, reason, 0);
        }
    }

    public Result clear(String demoEmail) {
        Optional<User> demo = userRepository.findByEmailIgnoreCase(demoEmail == null ? "" : demoEmail.trim());
        if (demo.isEmpty()) {
            return Result.skipped("the demo account " + demoEmail + " does not exist");
        }
        Long businessPkid = businessService.ownedBy(demo.get().getPkid()).getPkid();
        if (!hasSampleData(businessPkid)) {
            return Result.skipped("the account " + demoEmail + " holds no sample data");
        }
        int records = transactionTemplate.execute(status -> STATEMENTS.stream()
                .mapToInt(statement -> jdbcTemplate.update(statement, businessPkid))
                .sum());
        return new Result(true, null, records);
    }

    private boolean hasSampleData(Long businessPkid) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select sample_data from businesses where pkid = ?", Boolean.class, businessPkid));
    }
}
