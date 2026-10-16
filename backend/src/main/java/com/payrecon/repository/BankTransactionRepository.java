package com.payrecon.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.payrecon.model.BankTransaction;

@Repository
public class BankTransactionRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public BankTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(List<BankTransaction> transactions) {
        String sql = """
                INSERT INTO bank_transactions
                    (external_id, description, amount, currency, posted_date)
                VALUES (?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, transactions, BATCH_SIZE, (ps, b) -> {
            ps.setString(1, b.externalId());
            ps.setString(2, b.description());
            ps.setLong(3, b.amount());
            ps.setString(4, b.currency());
            ps.setObject(5, b.postedDate());
        });
    }
}