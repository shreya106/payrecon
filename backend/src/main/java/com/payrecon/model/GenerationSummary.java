package com.payrecon.model;

public record GenerationSummary(
        int processorTransactions,
        int ledgerEntries,
        int bankTransactions,
        int seededBreaks
) {}