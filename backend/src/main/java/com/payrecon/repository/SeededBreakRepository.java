package com.payrecon.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.payrecon.model.SeededBreak;

@Repository
public class SeededBreakRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public SeededBreakRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(List<SeededBreak> breaks) {
        String sql = """
                INSERT INTO seeded_breaks (break_type, external_ids, note)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, breaks, BATCH_SIZE, (ps, s) -> {
            ps.setString(1, s.breakType().name());
            ps.setArray(2, ps.getConnection().createArrayOf("text", s.externalIds().toArray()));
            ps.setString(3, s.note());
        });
    }
}