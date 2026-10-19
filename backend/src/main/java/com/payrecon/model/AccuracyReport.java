package com.payrecon.model;

public record AccuracyReport(
        int seededBreaks,   // breaks in the answer key
        int detected,       // seeded breaks the engine found
        int missed,         // seeded breaks the engine did NOT find
        int falseAlarms     // breaks the engine reported that aren't in the key
) {}