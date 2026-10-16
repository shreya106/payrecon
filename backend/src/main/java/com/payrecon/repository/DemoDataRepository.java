package com.payrecon.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DemoDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public DemoDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clearAll() {
        jdbcTemplate.execute("""
                TRUNCATE processor_transactions,
                         ledger_entries,
                         bank_transactions,
                         seeded_breaks,
                         recon_runs
                RESTART IDENTITY CASCADE
                """);
    }
}