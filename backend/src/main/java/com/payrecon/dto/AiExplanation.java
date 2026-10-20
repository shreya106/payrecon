package com.payrecon.dto;

import java.time.OffsetDateTime;

public record AiExplanation(
        String cause,
        String confidence,
        String suggestedFix,
        OffsetDateTime explainedAt
) {}