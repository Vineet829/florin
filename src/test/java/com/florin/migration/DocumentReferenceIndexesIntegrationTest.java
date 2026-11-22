package com.florin.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DocumentReferenceIndexesIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void indexesDocumentReferences() {
        assertThat(jdbcTemplate.queryForList(
                        "select tablename from pg_indexes where indexdef like '%(document_pkid)' order by tablename",
                        String.class))
                .contains("notifications", "recurring_runs");
    }
}
