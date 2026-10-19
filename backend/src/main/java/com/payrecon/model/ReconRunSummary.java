package com.payrecon.model;

import java.util.Map;

public record ReconRunSummary(
        long runId,
        int totalGroups,
        int matched,
        int breaks,
        Map<BreakType, Long> breaksByType,
        AccuracyReport accuracy
) {}