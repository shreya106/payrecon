package com.payrecon.repository;

import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.payrecon.model.LedgerEntry;

@Repository
public class LedgerEntryRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public LedgerEntryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(List<LedgerEntry> entries) {
        String sql = """
                INSERT INTO ledger_entries
                    (external_id, processor_ref, amount, currency, description, entry_date)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, entries, BATCH_SIZE, (ps, e) -> {
            ps.setString(1, e.externalId());
            ps.setString(2, e.processorRef());
            ps.setLong(3, e.amount());
            ps.setString(4, e.currency());
            ps.setString(5, e.description());
            ps.setObject(6, e.entryDate().atOffset(ZoneOffset.UTC));
        });
    }
}