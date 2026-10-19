package com.payrecon.repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.payrecon.model.GroupItem;
import com.payrecon.model.ReconGroup;

@Repository
public class ReconGroupRepository {

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public ReconGroupRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(long runId, List<ReconGroup> groups) {
        List<Object[]> itemRows = new ArrayList<>();

        for (ReconGroup group : groups) {
            // 1. Save the group and get back the id Postgres created
            long groupId = jdbcClient.sql("""
                    INSERT INTO recon_groups (run_id, outcome, match_method, break_type, amount_impact)
                    VALUES (:runId, :outcome, :matchMethod, :breakType, :amountImpact)
                    RETURNING id
                    """)
                    .param("runId", runId)
                    .param("outcome", group.outcome().name())
                    .param("matchMethod", group.matchMethod().name())
                    .param("breakType",
                            group.breakType() == null ? null : group.breakType().name(),
                            Types.VARCHAR)
                    .param("amountImpact", group.amountImpact())
                    .query(Long.class)
                    .single();

            // 2. Remember its items, to save them all together afterwards
            for (GroupItem item : group.items()) {
                itemRows.add(new Object[] { runId, groupId, item.source().name(), item.recordId() });
            }
        }

        // 3. Save all items in one batch
        jdbcTemplate.batchUpdate("""
                INSERT INTO recon_group_items (run_id, group_id, source, record_id)
                VALUES (?, ?, ?, ?)
                """, itemRows);
    }
}