package com.payrecon.dto;

import java.util.List;

public record BreakDetail(
        long groupId,
        long runId,
        String outcome,
        String breakType,
        String matchMethod,
        long amountImpactCents,
        AiExplanation explanation,     // null if not explained yet
        List<RecordView> records
) {}