package com.payrecon.model;

import java.time.LocalDate;

public record BankTransaction(
        Long id,
        String externalId,
        String description,
        long amount,
        String currency,
        LocalDate postedDate
) {}