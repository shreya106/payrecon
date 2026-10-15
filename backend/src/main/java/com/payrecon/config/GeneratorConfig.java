package com.payrecon.config;

import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payrecon.generator")
public record GeneratorConfig(
        int days,
        int salesPerDay,
        double breakRate,
        long seed,
        int payoutDelayBusinessDays,
        LocalDate asOfDate
) {}