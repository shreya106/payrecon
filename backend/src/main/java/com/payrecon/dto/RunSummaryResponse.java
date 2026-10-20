package com.payrecon.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record RunSummaryResponse(
        long runId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int totalGroups,
        int matched,
        int breaks,
        int explained,
        long amountAtRiskCents,
        Map<String, Long> breaksByType,
        AccuracyResponse accuracy      // null when the run has no answer key
) {}