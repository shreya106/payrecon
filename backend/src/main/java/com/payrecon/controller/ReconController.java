package com.payrecon.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payrecon.model.ReconRunSummary;
import com.payrecon.service.ReconService;

@RestController
@RequestMapping("/api/recon")
public class ReconController {

    private final ReconService reconService;

    public ReconController(ReconService reconService) {
        this.reconService = reconService;
    }

    @PostMapping("/run")
    public ReconRunSummary run() {
        return reconService.run();
    }
}