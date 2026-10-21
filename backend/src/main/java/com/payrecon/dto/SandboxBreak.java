package com.payrecon.dto;

import java.util.List;

public record SandboxBreak(
        int index,               // its position in the list: break #0, #1, #2...
        String breakType,
        long amountImpactCents,
        List<String> records     // one line of text per record, e.g. "Ledger L000567 | amount=4200 |"
) {}