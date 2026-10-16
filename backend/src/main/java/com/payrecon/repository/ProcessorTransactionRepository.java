package com.payrecon.repository;

import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.payrecon.model.ProcessorTransaction;

@Repository
public class ProcessorTransactionRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public ProcessorTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(List<ProcessorTransaction> transactions) {
        String sql = """
                INSERT INTO processor_transactions
                    (external_id, type, gross_amount, fee_amount, net_amount,
                     currency, payout_external_id, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, transactions, BATCH_SIZE, (ps, t) -> {
            ps.setString(1, t.externalId());
            ps.setString(2, t.type().name());
            ps.setLong(3, t.grossAmount());
            ps.setLong(4, t.feeAmount());
            ps.setLong(5, t.netAmount());
            ps.setString(6, t.currency());
            ps.setString(7, t.payoutExternalId());
            ps.setString(8, t.description());
            ps.setObject(9, t.createdAt().atOffset(ZoneOffset.UTC));
        });
    }
}