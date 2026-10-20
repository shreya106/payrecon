package com.payrecon.service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.payrecon.dto.BreakDetail;
import com.payrecon.dto.BreakRow;
import com.payrecon.dto.PageResponse;
import com.payrecon.dto.RunSummaryResponse;
import com.payrecon.model.BreakType;
import com.payrecon.repository.DashboardRepository;
import com.payrecon.repository.DashboardRepository.GroupStats;
import com.payrecon.repository.DashboardRepository.RunRow;

@Service
public class DashboardService {

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public RunSummaryResponse latestRunSummary() {
        long runId = repository.findLatestCompletedRunId()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No completed reconciliation runs yet"));
        return runSummary(runId);
    }

    public RunSummaryResponse runSummary(long runId) {
        RunRow run = repository.findRun(runId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Run " + runId + " not found"));

        int matched = 0;
        int breaks = 0;
        int explained = 0;
        long amountAtRisk = 0;
        Map<String, Long> breaksByType = new TreeMap<>();

        for (GroupStats s : repository.findGroupStats(runId)) {
            if ("MATCHED".equals(s.outcome())) {
                matched += (int) s.count();
            } else {
                breaks += (int) s.count();
                explained += (int) s.explained();
                breaksByType.put(s.breakType(), s.count());
                // In-transit money isn't at risk; it's just not here yet.
                if (!BreakType.IN_TRANSIT.name().equals(s.breakType())) {
                    amountAtRisk += s.amountImpact();
                }
            }
        }

        return new RunSummaryResponse(run.id(), run.status(), run.startedAt(), run.completedAt(),
                matched + breaks, matched, breaks, explained, amountAtRisk,
                breaksByType, run.accuracy());
    }

    public PageResponse<BreakRow> breaks(long runId, BreakType type, int page, int size) {
        repository.findRun(runId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Run " + runId + " not found"));

        String typeName = type == null ? null : type.name();
        long total = repository.countBreaks(runId, typeName);
        List<BreakRow> items = repository.findBreaks(runId, typeName, size, page * size);
        int totalPages = (int) ((total + size - 1) / size);

        return new PageResponse<>(items, page, size, total, totalPages);
    }

    public BreakDetail breakDetail(long groupId) {
        return repository.findBreakDetail(groupId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Break " + groupId + " not found"));
    }
}