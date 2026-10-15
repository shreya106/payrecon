package com.payrecon.model;

import java.time.Instant;

public record LedgerEntry(
        Long id,
        String externalId,
        String processorRef,
        long amount,
        String currency,
        String description,
        Instant entryDate
) {}