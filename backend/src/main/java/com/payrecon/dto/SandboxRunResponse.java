package com.payrecon.dto;

import java.util.List;
import java.util.Map;

public record SandboxRunResponse(
        long seed,                         // the browser keeps this, to ask for explanations later
        int totalGroups,
        int matched,
        int breaks,
        long amountAtRiskCents,
        Map<String, Long> breaksByType,
        AccuracyResponse accuracy,         // reuses the DTO from the dashboard
        List<SandboxBreak> breakList
) {}