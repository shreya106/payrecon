package com.payrecon.model;

import java.util.List;

public record PlantResult(
        GeneratedDataset dataset,
        List<SeededBreak> answerKey
) {}