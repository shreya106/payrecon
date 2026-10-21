package com.payrecon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.payrecon.model.AccuracyReport;
import com.payrecon.model.GenerationSummary;
import com.payrecon.model.ReconRunSummary;
import com.payrecon.service.DemoDataService;
import com.payrecon.service.ReconService;

@SpringBootTest(properties = "spring.ai.anthropic.api-key=test-key-not-used")
@Import(TestcontainersConfiguration.class)
class ReconPipelineIntegrationTest {

    @Autowired
    private DemoDataService demoDataService;

    @Autowired
    private ReconService reconService;

    @Test
    void endToEndRunDetectsEverySeededBreak() {
        GenerationSummary generated = demoDataService.generate();
        ReconRunSummary run = reconService.run();

        assertEquals(122, generated.seededBreaks());
        assertEquals(6061, run.totalGroups());
        assertEquals(5939, run.matched());
        assertEquals(122, run.breaks());

        AccuracyReport accuracy = run.accuracy();
        assertEquals(122, accuracy.detected());
        assertEquals(0, accuracy.missed());
        assertEquals(0, accuracy.falseAlarms());
    }
}