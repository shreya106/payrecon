package com.payrecon.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ProcessorTransactionType;

@Repository
public class ProcessorTransactionRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbcClient;

    public ProcessorTransactionRepository(JdbcTemplate jdbcTemplate, JdbcClient jdbcClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcClient = jdbcClient;
    }

    public List<ProcessorTransaction> findByType(ProcessorTransactionType type) {
        return jdbcClient.sql("""
                SELECT id, external_id, type, gross_amount, fee_amount, net_amount,
                       currency, payout_external_id, description, created_at
                FROM processor_transactions
                WHERE type = :type
                ORDER BY id
                """)
                .param("type", type.name())
                .query((rs, rowNum) -> new ProcessorTransaction(
                        rs.getLong("id"),
                        rs.getString("external_id"),
                        ProcessorTransactionType.valueOf(rs.getString("type")),
                        rs.getLong("gross_amount"),
                        rs.getLong("fee_amount"),
                        rs.getLong("net_amount"),
                        rs.getString("currency"),
                        rs.getString("payout_external_id"),
                        rs.getString("description"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
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