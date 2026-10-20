package com.payrecon.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.payrecon.dto.AccuracyResponse;
import com.payrecon.dto.AiExplanation;
import com.payrecon.dto.BreakDetail;
import com.payrecon.dto.BreakRow;
import com.payrecon.dto.RecordView;

@Repository
public class DashboardRepository {

    private final JdbcClient jdbcClient;

    public DashboardRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** One row of counts per (outcome, break type) in a run. */
    public record GroupStats(String outcome, String breakType, long count,
                             long amountImpact, long explained) {}

    /** The basic run row. */
    public record RunRow(long id, String status, OffsetDateTime startedAt,
                         OffsetDateTime completedAt, AccuracyResponse accuracy) {}

    public Optional<Long> findLatestCompletedRunId() {
        return jdbcClient.sql("""
                SELECT id FROM recon_runs
                WHERE status = 'COMPLETED'
                ORDER BY id DESC
                LIMIT 1
                """)
                .query(Long.class)
                .optional();
    }

    public Optional<RunRow> findRun(long runId) {
        return jdbcClient.sql("""
                SELECT id, status, started_at, completed_at,
                       seeded_breaks, detected, missed, false_alarms
                FROM recon_runs WHERE id = :id
                """)
                .param("id", runId)
                .query((rs, rowNum) -> {
                    Integer seeded = rs.getObject("seeded_breaks", Integer.class);
                    AccuracyResponse accuracy = seeded == null ? null : new AccuracyResponse(
                            seeded,
                            rs.getInt("detected"),
                            rs.getInt("missed"),
                            rs.getInt("false_alarms"));
                    return new RunRow(
                            rs.getLong("id"),
                            rs.getString("status"),
                            rs.getObject("started_at", OffsetDateTime.class),
                            rs.getObject("completed_at", OffsetDateTime.class),
                            accuracy);
                })
                .optional();
    }

    public List<GroupStats> findGroupStats(long runId) {
        return jdbcClient.sql("""
                SELECT outcome, break_type,
                       COUNT(*)           AS n,
                       SUM(amount_impact) AS amount,
                       COUNT(ai_cause)    AS explained
                FROM recon_groups
                WHERE run_id = :runId
                GROUP BY outcome, break_type
                """)
                .param("runId", runId)
                .query((rs, rowNum) -> new GroupStats(
                        rs.getString("outcome"),
                        rs.getString("break_type"),
                        rs.getLong("n"),
                        rs.getLong("amount"),
                        rs.getLong("explained")))
                .list();
    }

    public long countBreaks(long runId, String breakType) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM recon_groups
                WHERE run_id = :runId
                  AND outcome = 'BREAK'
                  AND (CAST(:type AS TEXT) IS NULL OR break_type = CAST(:type AS TEXT))
                """)
                .param("runId", runId)
                .param("type", breakType, Types.VARCHAR)
                .query(Long.class)
                .single();
    }

    public List<BreakRow> findBreaks(long runId, String breakType, int size, int offset) {
        return jdbcClient.sql("""
                SELECT id, break_type, amount_impact, ai_confidence, ai_cause
                FROM recon_groups
                WHERE run_id = :runId
                  AND outcome = 'BREAK'
                  AND (CAST(:type AS TEXT) IS NULL OR break_type = CAST(:type AS TEXT))
                ORDER BY amount_impact DESC, id
                LIMIT :size OFFSET :offset
                """)
                .param("runId", runId)
                .param("type", breakType, Types.VARCHAR)
                .param("size", size)
                .param("offset", offset)
                .query((rs, rowNum) -> new BreakRow(
                        rs.getLong("id"),
                        rs.getString("break_type"),
                        rs.getLong("amount_impact"),
                        rs.getString("ai_confidence"),
                        rs.getString("ai_cause")))
                .list();
    }

    public Optional<BreakDetail> findBreakDetail(long groupId) {
        Optional<BreakDetail> header = jdbcClient.sql("""
                SELECT id, run_id, outcome, break_type, match_method, amount_impact,
                       ai_cause, ai_confidence, ai_suggested_fix, explained_at
                FROM recon_groups WHERE id = :id
                """)
                .param("id", groupId)
                .query((rs, rowNum) -> {
                    String cause = rs.getString("ai_cause");
                    AiExplanation explanation = cause == null ? null : new AiExplanation(
                            cause,
                            rs.getString("ai_confidence"),
                            rs.getString("ai_suggested_fix"),
                            rs.getObject("explained_at", OffsetDateTime.class));
                    return new BreakDetail(
                            rs.getLong("id"),
                            rs.getLong("run_id"),
                            rs.getString("outcome"),
                            rs.getString("break_type"),
                            rs.getString("match_method"),
                            rs.getLong("amount_impact"),
                            explanation,
                            List.of());
                })
                .optional();

        if (header.isEmpty()) {
            return Optional.empty();
        }

        List<RecordView> records = jdbcClient.sql("""
                SELECT i.source, i.record_id,
                       COALESCE(p.external_id, l.external_id, b.external_id) AS external_id,
                       p.type,
                       COALESCE(p.description, l.description, b.description) AS description,
                       p.gross_amount, p.fee_amount, p.net_amount,
                       COALESCE(l.amount, b.amount) AS amount,
                       l.processor_ref,
                       p.payout_external_id,
                       COALESCE(CAST(p.created_at AS TEXT),
                                CAST(l.entry_date AS TEXT),
                                CAST(b.posted_date AS TEXT)) AS record_date
                FROM recon_group_items i
                LEFT JOIN processor_transactions p ON i.source = 'PROCESSOR' AND p.id = i.record_id
                LEFT JOIN ledger_entries l        ON i.source = 'LEDGER'    AND l.id = i.record_id
                LEFT JOIN bank_transactions b     ON i.source = 'BANK'      AND b.id = i.record_id
                WHERE i.group_id = :id
                ORDER BY i.source DESC, i.record_id
                """)
                .param("id", groupId)
                .query((rs, rowNum) -> new RecordView(
                        rs.getString("source"),
                        rs.getLong("record_id"),
                        rs.getString("external_id"),
                        rs.getString("type"),
                        rs.getString("description"),
                        rs.getObject("gross_amount", Long.class),
                        rs.getObject("fee_amount", Long.class),
                        rs.getObject("net_amount", Long.class),
                        rs.getObject("amount", Long.class),
                        rs.getString("processor_ref"),
                        rs.getString("payout_external_id"),
                        rs.getString("record_date")))
                .list();

        BreakDetail h = header.get();
        return Optional.of(new BreakDetail(h.groupId(), h.runId(), h.outcome(), h.breakType(),
                h.matchMethod(), h.amountImpactCents(), h.explanation(), records));
    }
}