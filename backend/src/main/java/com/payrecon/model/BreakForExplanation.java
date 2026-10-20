package com.payrecon.model;

import java.util.List;

public record BreakForExplanation(
        long groupId,
        BreakType breakType,
        long amountImpact,
        List<String> evidence     // one line of text per record in the group
) {}