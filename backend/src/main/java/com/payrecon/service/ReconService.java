package com.payrecon.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payrecon.config.GeneratorConfig;
import com.payrecon.model.AccuracyReport;
import com.payrecon.model.BankTransaction;
import com.payrecon.model.BreakType;
import com.payrecon.model.GroupItem;
import com.payrecon.model.LedgerEntry;
import com.payrecon.model.Outcome;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ProcessorTransactionType;
import com.payrecon.model.ReconGroup;
import com.payrecon.model.ReconRunSummary;
import com.payrecon.model.Source;
import com.payrecon.repository.BankTransactionRepository;
import com.payrecon.repository.LedgerEntryRepository;
import com.payrecon.repository.ProcessorTransactionRepository;
import com.payrecon.repository.ReconGroupRepository;
import com.payrecon.repository.ReconRunRepository;

@Service
public class ReconService {

    private final GeneratorConfig config;
    private final ProcessorTransactionRepository processorRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final BankTransactionRepository bankRepository;
    private final ReconRunRepository runRepository;
    private final ReconGroupRepository groupRepository;
    private final ExactIdMatcher exactIdMatcher;
    private final PayoutBankMatcher payoutBankMatcher;
    private final AccuracyChecker accuracyChecker;

    public ReconService(GeneratorConfig config,
                        ProcessorTransactionRepository processorRepository,
                        LedgerEntryRepository ledgerRepository,
                        BankTransactionRepository bankRepository,
                        ReconRunRepository runRepository,
                        ReconGroupRepository groupRepository,
                        ExactIdMatcher exactIdMatcher,
                        PayoutBankMatcher payoutBankMatcher,
                        AccuracyChecker accuracyChecker) {
        this.config = config;
        this.processorRepository = processorRepository;
        this.ledgerRepository = ledgerRepository;
        this.bankRepository = bankRepository;
        this.runRepository = runRepository;
        this.groupRepository = groupRepository;
        this.exactIdMatcher = exactIdMatcher;
        this.payoutBankMatcher = payoutBankMatcher;
        this.accuracyChecker = accuracyChecker;
    }

    @Transactional
    public ReconRunSummary run() {
        // 1. Start a run
        long runId = runRepository.start();

        // 2. Load the data
        List<ProcessorTransaction> charges = processorRepository.findByType(ProcessorTransactionType.CHARGE);
        List<ProcessorTransaction> payouts = processorRepository.findByType(ProcessorTransactionType.PAYOUT);
        List<LedgerEntry> ledger = ledgerRepository.findAll();
        List<BankTransaction> bank = bankRepository.findAll();


        // 3. Run both passes
        List<ReconGroup> groups = new ArrayList<>();
        groups.addAll(exactIdMatcher.match(charges, ledger));
        groups.addAll(payoutBankMatcher.match(payouts, bank, config.asOfDate()));


        // 4. Save results and finish the run
        groupRepository.saveAll(runId, groups);
        runRepository.complete(runId);

        // 5. Grade the results against the answer key
        List<ProcessorTransaction> allProcessor = new ArrayList<>(charges);
        allProcessor.addAll(payouts);
        AccuracyReport accuracy = accuracyChecker.check(groups, allProcessor, ledger, bank);

        // 6. Build the summary
        int matched = (int) groups.stream().filter(g -> g.outcome() == Outcome.MATCHED).count();
        Map<BreakType, Long> breaksByType = groups.stream()
                .filter(g -> g.outcome() == Outcome.BREAK)
                .collect(Collectors.groupingBy(ReconGroup::breakType, Collectors.counting()));

        return new ReconRunSummary(runId, groups.size(), matched,
                groups.size() - matched, breaksByType, accuracy);
    }
}