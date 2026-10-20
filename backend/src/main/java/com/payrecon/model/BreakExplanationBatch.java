package com.payrecon.model;

import java.util.List;

public record BreakExplanationBatch(
        List<BreakExplanation> explanations
) {}