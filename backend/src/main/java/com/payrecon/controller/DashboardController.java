package com.payrecon.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payrecon.dto.BreakDetail;
import com.payrecon.dto.BreakRow;
import com.payrecon.dto.PageResponse;
import com.payrecon.dto.RunSummaryResponse;
import com.payrecon.model.BreakType;
import com.payrecon.service.DashboardService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/recon")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/runs/latest")
    public RunSummaryResponse latestRun() {
        return dashboardService.latestRunSummary();
    }

    @GetMapping("/runs/{runId}/breaks")
    public PageResponse<BreakRow> breaks(
            @PathVariable long runId,
            @RequestParam(required = false) BreakType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return dashboardService.breaks(runId, type, page, size);
    }

    @GetMapping("/breaks/{groupId}")
    public BreakDetail breakDetail(@PathVariable long groupId) {
        return dashboardService.breakDetail(groupId);
    }
}