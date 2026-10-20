package com.payrecon.model;

public record ExplainSummary(
        long runId,
        int breaksToExplain,
        int explained,
        int failedBatches
) {}