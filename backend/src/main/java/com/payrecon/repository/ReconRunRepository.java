package com.payrecon.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReconRunRepository {

    private final JdbcClient jdbcClient;

    public ReconRunRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Creates a new run and returns its id. */
    public long start() {
        return jdbcClient.sql("INSERT INTO recon_runs (status) VALUES ('RUNNING') RETURNING id")
                .query(Long.class)
                .single();
    }

    public void complete(long runId) {
        jdbcClient.sql("UPDATE recon_runs SET status = 'COMPLETED', completed_at = now() WHERE id = :id")
                .param("id", runId)
                .update();
    }
}