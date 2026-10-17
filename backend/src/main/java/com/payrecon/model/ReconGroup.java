package com.payrecon.model;

import java.util.List;

public record ReconGroup(
        Outcome outcome,
        MatchMethod matchMethod,
        BreakType breakType,      // null when MATCHED
        long amountImpact,        // money affected, in cents (0 when MATCHED)
        List<GroupItem> items
) {}