package com.payrecon.model;

import java.time.Instant;

public record ProcessorTransaction(
        Long id,
        String externalId,
        ProcessorTransactionType type,
        long grossAmount,
        long feeAmount,
        long netAmount,
        String currency,
        String payoutExternalId,
        String description,
        Instant createdAt
) {}