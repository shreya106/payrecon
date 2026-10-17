package com.payrecon.service;

import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payrecon.config.GeneratorConfig;
import com.payrecon.model.GeneratedDataset;
import com.payrecon.model.GenerationSummary;
import com.payrecon.model.PlantResult;
import com.payrecon.repository.BankTransactionRepository;
import com.payrecon.repository.DemoDataRepository;
import com.payrecon.repository.LedgerEntryRepository;
import com.payrecon.repository.ProcessorTransactionRepository;
import com.payrecon.repository.SeededBreakRepository;

@Service
public class DemoDataService {

    private final GeneratorConfig config;
    private final DataGenerator dataGenerator;
    private final BreakPlanter breakPlanter;
    private final DemoDataRepository demoDataRepository;
    private final ProcessorTransactionRepository processorRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final BankTransactionRepository bankRepository;
    private final SeededBreakRepository seededBreakRepository;

    public DemoDataService(GeneratorConfig config,
                           DataGenerator dataGenerator,
                           BreakPlanter breakPlanter,
                           DemoDataRepository demoDataRepository,
                           ProcessorTransactionRepository processorRepository,
                           LedgerEntryRepository ledgerRepository,
                           BankTransactionRepository bankRepository,
                           SeededBreakRepository seededBreakRepository) {
        this.config = config;
        this.dataGenerator = dataGenerator;
        this.breakPlanter = breakPlanter;
        this.demoDataRepository = demoDataRepository;
        this.processorRepository = processorRepository;
        this.ledgerRepository = ledgerRepository;
        this.bankRepository = bankRepository;
        this.seededBreakRepository = seededBreakRepository;
    }

    @Transactional
    public GenerationSummary generate() {
        // 1. One Random for the whole run, so the seed controls everything
        Random random = new Random(config.seed());

        // 2. Make clean data, then plant mistakes
        GeneratedDataset clean = dataGenerator.generate(random);
        PlantResult result = breakPlanter.plant(clean, random);
        GeneratedDataset data = result.dataset();

        // 3. Empty the tables, then save everything
        demoDataRepository.clearAll();
        processorRepository.saveAll(data.processorTransactions());
        ledgerRepository.saveAll(data.ledgerEntries());
        bankRepository.saveAll(data.bankTransactions());
        seededBreakRepository.saveAll(result.answerKey());

        // 4. Report what was saved
        return new GenerationSummary(
                data.processorTransactions().size(),
                data.ledgerEntries().size(),
                data.bankTransactions().size(),
                result.answerKey().size());
    }
}