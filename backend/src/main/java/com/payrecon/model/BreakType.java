package com.payrecon.model;

public enum BreakType {
    MISSING_IN_LEDGER,
    MISSING_IN_PROCESSOR,
    DUPLICATE,
    AMOUNT_MISMATCH,
    IN_TRANSIT,
    MISSING_IN_BANK,
    UNEXPLAINED
}