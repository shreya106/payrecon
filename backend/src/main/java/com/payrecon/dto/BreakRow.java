package com.payrecon.dto;

public record BreakRow(
        long groupId,
        String breakType,
        long amountImpactCents,
        String aiConfidence,
        String aiCause
) {}