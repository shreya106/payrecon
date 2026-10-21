package com.payrecon.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.payrecon.dto.SandboxExplanationResponse;
import com.payrecon.dto.SandboxRunResponse;
import com.payrecon.service.SandboxService;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final SandboxService sandboxService;

    public SandboxController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    /** GET: it changes nothing on the server. Without a seed, a random one is picked. */
    @GetMapping("/run")
    public SandboxRunResponse run(@RequestParam(required = false) Long seed) {
        return sandboxService.run(seed);
    }

    /** POST: it uses up some of today's AI budget, so it isn't a pure read. */
    @PostMapping("/explain")
    public SandboxExplanationResponse explain(@RequestParam long seed, @RequestParam int index) {
        return sandboxService.explain(seed, index);
    }
}