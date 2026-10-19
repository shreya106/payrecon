package com.payrecon.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.payrecon.model.LedgerEntry;

@Repository
public class LedgerEntryRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbcClient;

    public LedgerEntryRepository(JdbcTemplate jdbcTemplate, JdbcClient jdbcClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcClient = jdbcClient;
    }

    public List<LedgerEntry> findAll() {
    return jdbcClient.sql("""
            SELECT id, external_id, processor_ref, amount, currency, description, entry_date
            FROM ledger_entries
            ORDER BY id
            """)
            .query((rs, rowNum) -> new LedgerEntry(
                    rs.getLong("id"),
                    rs.getString("external_id"),
                    rs.getString("processor_ref"),
                    rs.getLong("amount"),
                    rs.getString("currency"),
                    rs.getString("description"),
                    rs.getObject("entry_date", OffsetDateTime.class).toInstant()))
            .list();
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