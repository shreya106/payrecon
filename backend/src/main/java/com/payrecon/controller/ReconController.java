package com.payrecon.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payrecon.model.ExplainSummary;
import com.payrecon.model.ReconRunSummary;
import com.payrecon.service.ExplanationService;
import com.payrecon.service.ReconService;

@RestController
@RequestMapping("/api/recon")
public class ReconController {

    private final ReconService reconService;
    private final ExplanationService explanationService;

    public ReconController(ReconService reconService, ExplanationService explanationService) {
        this.reconService = reconService;
        this.explanationService = explanationService;
    }

    @PostMapping("/run")
    public ReconRunSummary run() {
        return reconService.run();
    }

    @PostMapping("/{runId}/explain")
    public ExplainSummary explain(@PathVariable long runId) {
        return explanationService.explain(runId);
    }
}