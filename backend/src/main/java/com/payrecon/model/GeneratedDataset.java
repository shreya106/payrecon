package com.payrecon.model;

import java.util.List;

public record GeneratedDataset(
        List<ProcessorTransaction> processorTransactions,
        List<LedgerEntry> ledgerEntries,
        List<BankTransaction> bankTransactions
) {}