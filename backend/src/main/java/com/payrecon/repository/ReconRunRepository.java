package com.payrecon.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.payrecon.model.AccuracyReport;

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
        jdbcClient.sql("UPDATE recon_runs SET status = 'COMPLETED', completed_at = clock_timestamp() WHERE id = :id")
                .param("id", runId)
                .update();
    }
    public void saveAccuracy(long runId, AccuracyReport accuracy) {
    jdbcClient.sql("""
            UPDATE recon_runs
            SET seeded_breaks = :seeded, detected = :detected,
                missed = :missed, false_alarms = :falseAlarms
            WHERE id = :id
            """)
            .param("seeded", accuracy.seededBreaks())
            .param("detected", accuracy.detected())
            .param("missed", accuracy.missed())
            .param("falseAlarms", accuracy.falseAlarms())
            .param("id", runId)
            .update();
}
}