package com.payrecon.dto;

public record RecordView(
        String source,
        long recordId,
        String externalId,
        String type,
        String description,
        Long grossAmountCents,
        Long feeAmountCents,
        Long netAmountCents,
        Long amountCents,
        String processorRef,
        String payoutExternalId,
        String date
) {}