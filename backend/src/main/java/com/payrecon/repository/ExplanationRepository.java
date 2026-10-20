package com.payrecon.repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.payrecon.model.BreakExplanation;
import com.payrecon.model.BreakForExplanation;
import com.payrecon.model.BreakType;

@Repository
public class ExplanationRepository {

    private final JdbcClient jdbcClient;

    public ExplanationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Breaks in this run that don't have an AI explanation yet, with their records as text. */
    public List<BreakForExplanation> findUnexplained(long runId) {
        Map<Long, BreakForExplanation> byGroup = new LinkedHashMap<>();

        jdbcClient.sql("""
                SELECT g.id AS group_id, g.break_type, g.amount_impact,
                       CASE i.source
                         WHEN 'PROCESSOR' THEN format(
                             'Stripe %s %s | gross=%s fee=%s net=%s | created=%s | payout=%s',
                             p.type, p.external_id, p.gross_amount, p.fee_amount, p.net_amount,
                             p.created_at, coalesce(p.payout_external_id, 'none'))
                         WHEN 'LEDGER' THEN format(
                             'Ledger %s | processor_ref=%s | amount=%s | date=%s | %s',
                             l.external_id, l.processor_ref, l.amount, l.entry_date, l.description)
                         WHEN 'BANK' THEN format(
                             'Bank %s | %s | amount=%s | posted=%s',
                             b.external_id, b.description, b.amount, b.posted_date)
                       END AS evidence
                FROM recon_groups g
                JOIN recon_group_items i ON i.group_id = g.id
                LEFT JOIN processor_transactions p ON i.source = 'PROCESSOR' AND p.id = i.record_id
                LEFT JOIN ledger_entries l        ON i.source = 'LEDGER'    AND l.id = i.record_id
                LEFT JOIN bank_transactions b     ON i.source = 'BANK'      AND b.id = i.record_id
                WHERE g.run_id = :runId
                  AND g.outcome = 'BREAK'
                  AND g.ai_cause IS NULL
                ORDER BY g.id
                """)
                .param("runId", runId)
                .query((RowCallbackHandler) rs -> {
                    long groupId = rs.getLong("group_id");
                    BreakForExplanation group = byGroup.get(groupId);
                    if (group == null) {
                        group = new BreakForExplanation(
                                groupId,
                                BreakType.valueOf(rs.getString("break_type")),
                                rs.getLong("amount_impact"),
                                new ArrayList<>());
                        byGroup.put(groupId, group);
                    }
                    group.evidence().add(rs.getString("evidence"));
                });

        return new ArrayList<>(byGroup.values());
    }

    /** Saves one explanation. Returns 1 if a row was updated, 0 if not. */
    public int save(long runId, BreakExplanation e) {
        return jdbcClient.sql("""
                UPDATE recon_groups
                SET ai_cause = :cause,
                    ai_confidence = :confidence,
                    ai_suggested_fix = :fix,
                    explained_at = now()
                WHERE id = :groupId AND run_id = :runId AND outcome = 'BREAK'
                """)
                .param("cause", e.likelyCause())
                .param("confidence", e.confidence() == null ? null : e.confidence().name(), Types.VARCHAR)
                .param("fix", e.suggestedFix())
                .param("groupId", e.groupId())
                .param("runId", runId)
                .update();
    }
}