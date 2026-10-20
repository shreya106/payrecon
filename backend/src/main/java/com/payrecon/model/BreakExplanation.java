package com.payrecon.model;

public record BreakExplanation(
        long groupId,
        String likelyCause,
        Confidence confidence,
        String suggestedFix
) {}