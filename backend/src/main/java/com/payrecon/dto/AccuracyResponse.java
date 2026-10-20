package com.payrecon.dto;

public record AccuracyResponse(
        int seededBreaks,
        int detected,
        int missed,
        int falseAlarms
) {}