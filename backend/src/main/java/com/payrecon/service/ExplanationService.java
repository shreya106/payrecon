package com.payrecon.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.payrecon.model.BreakExplanation;
import com.payrecon.model.BreakForExplanation;
import com.payrecon.model.ExplainSummary;
import com.payrecon.repository.ExplanationRepository;

@Service
public class ExplanationService {

    private static final Logger log = LoggerFactory.getLogger(ExplanationService.class);

    private static final int BATCH_SIZE = 10;
    private static final long PAUSE_BETWEEN_BATCHES_MS = 1_000;  

    private final ExplanationRepository repository;
    private final BreakExplainer explainer;

    public ExplanationService(ExplanationRepository repository, BreakExplainer explainer) {
        this.repository = repository;
        this.explainer = explainer;
    }

    public ExplainSummary explain(long runId) {
        List<BreakForExplanation> pending = repository.findUnexplained(runId);
        int explained = 0;
        int failedBatches = 0;

        for (int start = 0; start < pending.size(); start += BATCH_SIZE) {
            List<BreakForExplanation> batch =
                    pending.subList(start, Math.min(start + BATCH_SIZE, pending.size()));

            if (start > 0) {
                pause();
            }

            try {
                // Only accept answers for breaks we actually sent, and only once each
                Set<Long> expectedIds = new HashSet<>();
                for (BreakForExplanation b : batch) {
                    expectedIds.add(b.groupId());
                }

                for (BreakExplanation e : explainer.explain(batch)) {
                    if (expectedIds.remove(e.groupId())) {
                        explained += repository.save(runId, e);
                    } else {
                        log.warn("Ignoring explanation for unexpected groupId {}", e.groupId());
                    }
                }
            } catch (Exception ex) {
                failedBatches++;
                log.warn("Batch starting at {} failed: {}", start, ex.getMessage());
            }
        }

        return new ExplainSummary(runId, pending.size(), explained, failedBatches);
    }

    private void pause() {
        try {
            Thread.sleep(PAUSE_BETWEEN_BATCHES_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}