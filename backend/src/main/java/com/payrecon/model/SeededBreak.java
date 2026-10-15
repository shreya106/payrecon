package com.payrecon.model;

import java.util.List;

public record SeededBreak(
        BreakType breakType,
        List<String> externalIds,
        String note
) {}