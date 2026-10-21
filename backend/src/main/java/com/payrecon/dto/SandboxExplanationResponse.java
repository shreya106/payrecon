package com.payrecon.dto;

public record SandboxExplanationResponse(
        String cause,
        String confidence,
        String suggestedFix,
        int aiExplanationsLeftToday        // so the website can show "37 left today"
) {}