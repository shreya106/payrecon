package com.payrecon.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.payrecon.config.GeneratorConfig;
import com.payrecon.dto.AccuracyResponse;
import com.payrecon.dto.SandboxBreak;
import com.payrecon.dto.SandboxExplanationResponse;
import com.payrecon.dto.SandboxRunResponse;
import com.payrecon.model.AccuracyReport;
import com.payrecon.model.BankTransaction;
import com.payrecon.model.BreakExplanation;
import com.payrecon.model.BreakForExplanation;
import com.payrecon.model.BreakType;
import com.payrecon.model.GeneratedDataset;
import com.payrecon.model.GroupItem;
import com.payrecon.model.LedgerEntry;
import com.payrecon.model.Outcome;
import com.payrecon.model.PlantResult;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ProcessorTransactionType;
import com.payrecon.model.ReconGroup;

/**
 * Lets visitors reconcile a brand-new month of data, entirely in memory.
 * Nothing is saved: the seed is enough to rebuild the exact same month later.
 */
@Service
public class SandboxService {

    private final GeneratorConfig config;
    private final DataGenerator dataGenerator;
    private final BreakPlanter breakPlanter;
    private final ExactIdMatcher exactIdMatcher;
    private final PayoutBankMatcher payoutBankMatcher;
    private final AccuracyChecker accuracyChecker;
    private final BreakExplainer breakExplainer;
    private final int dailyAiLimit;

    // Daily AI counter (resets each day, UTC)
    private LocalDate counterDate = LocalDate.now(ZoneOffset.UTC);
    private int aiCallsToday = 0;

    // Remembers up to 500 recent explanations, so repeat clicks don't call the AI again
    private final Map<String, BreakExplanation> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BreakExplanation> eldest) {
            return size() > 500;
        }
    };

    public SandboxService(GeneratorConfig config,
                          DataGenerator dataGenerator,
                          BreakPlanter breakPlanter,
                          ExactIdMatcher exactIdMatcher,
                          PayoutBankMatcher payoutBankMatcher,
                          AccuracyChecker accuracyChecker,
                          BreakExplainer breakExplainer,
                          @Value("${payrecon.sandbox.daily-ai-limit:50}") int dailyAiLimit) {
        this.config = config;
        this.dataGenerator = dataGenerator;
        this.breakPlanter = breakPlanter;
        this.exactIdMatcher = exactIdMatcher;
        this.payoutBankMatcher = payoutBankMatcher;
        this.accuracyChecker = accuracyChecker;
        this.breakExplainer = breakExplainer;
        this.dailyAiLimit = dailyAiLimit;
    }

    // =========================================================
    // Part 1: run a new sandbox month
    // =========================================================

    public SandboxRunResponse run(Long requestedSeed) {
        long seed = requestedSeed != null ? requestedSeed : ThreadLocalRandom.current().nextLong(1, 1_000_000);
        Reconciled r = reconcile(seed);

        int matched = (int) r.groups().stream().filter(g -> g.outcome() == Outcome.MATCHED).count();
        long amountAtRisk = 0;
        Map<String, Long> breaksByType = new TreeMap<>();
        List<SandboxBreak> breakList = new ArrayList<>();

        for (int i = 0; i < r.breaks().size(); i++) {
            ReconGroup g = r.breaks().get(i);
            breaksByType.merge(g.breakType().name(), 1L, Long::sum);
            if (g.breakType() != BreakType.IN_TRANSIT) {
                amountAtRisk += g.amountImpact();
            }
            breakList.add(new SandboxBreak(i, g.breakType().name(), g.amountImpact(), r.evidenceFor(g)));
        }

        AccuracyReport a = r.accuracy();
        return new SandboxRunResponse(seed, r.groups().size(), matched, r.breaks().size(), amountAtRisk,
                breaksByType,
                new AccuracyResponse(a.seededBreaks(), a.detected(), a.missed(), a.falseAlarms()),
                breakList);
    }

    // =========================================================
    // Part 2: explain one sandbox break with AI (capped per day)
    // =========================================================

    public SandboxExplanationResponse explain(long seed, int index) {
        String cacheKey = seed + ":" + index;
        BreakExplanation cached;
        synchronized (cache) {
            cached = cache.get(cacheKey);
        }
        if (cached != null) {
            return toResponse(cached);   // already explained: no AI call, no limit used
        }

        Reconciled r = reconcile(seed);
        if (index < 0 || index >= r.breaks().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No break #" + index + " in this sandbox run");
        }

        useOneAiCall();   // throws 429 when today's limit is reached

        ReconGroup g = r.breaks().get(index);
        BreakForExplanation input = new BreakForExplanation(index, g.breakType(), g.amountImpact(), r.evidenceFor(g));

        BreakExplanation explanation = breakExplainer.explain(List.of(input), config.asOfDate()).stream()
                .filter(e -> e.groupId() == index)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "The AI didn't return an explanation. Please try again."));

        synchronized (cache) {
            cache.put(cacheKey, explanation);
        }
        return toResponse(explanation);
    }

    // =========================================================
    // Part 3: the shared recipe: seed -> month -> results
    // =========================================================

    /** Everything about one sandbox month, rebuilt from its seed. */
    private record Reconciled(
            List<ReconGroup> groups,
            List<ReconGroup> breaks,
            AccuracyReport accuracy,
            Map<Long, ProcessorTransaction> processorById,
            Map<Long, LedgerEntry> ledgerById,
            Map<Long, BankTransaction> bankById) {

        /** One line of text per record in the group, for the website and the AI. */
        List<String> evidenceFor(ReconGroup group) {
            List<String> lines = new ArrayList<>();
            for (GroupItem item : group.items()) {
                switch (item.source()) {
                    case PROCESSOR -> {
                        ProcessorTransaction p = processorById.get(item.recordId());
                        lines.add("Stripe %s %s | gross=%d fee=%d net=%d | created=%s | payout=%s".formatted(
                                p.type(), p.externalId(), p.grossAmount(), p.feeAmount(), p.netAmount(),
                                p.createdAt(), p.payoutExternalId() == null ? "none" : p.payoutExternalId()));
                    }
                    case LEDGER -> {
                        LedgerEntry l = ledgerById.get(item.recordId());
                        lines.add("Ledger %s | processor_ref=%s | amount=%d | date=%s | %s".formatted(
                                l.externalId(), l.processorRef(), l.amount(), l.entryDate(), l.description()));
                    }
                    case BANK -> {
                        BankTransaction b = bankById.get(item.recordId());
                        lines.add("Bank %s | %s | amount=%d | posted=%s".formatted(
                                b.externalId(), b.description(), b.amount(), b.postedDate()));
                    }
                }
            }
            return lines;
        }
    }

    private Reconciled reconcile(long seed) {
        Random random = new Random(seed);
        double breakRate = 0.01 + Math.floorMod(seed, 31) / 1000.0;   // 1% to 4%, fixed per seed

        GeneratedDataset clean = dataGenerator.generate(random);
        PlantResult planted = breakPlanter.plant(clean, random, breakRate);
        GeneratedDataset data = withIds(planted.dataset());

        List<ProcessorTransaction> charges = data.processorTransactions().stream()
                .filter(p -> p.type() == ProcessorTransactionType.CHARGE).toList();
        List<ProcessorTransaction> payouts = data.processorTransactions().stream()
                .filter(p -> p.type() == ProcessorTransactionType.PAYOUT).toList();

        List<ReconGroup> groups = new ArrayList<>(exactIdMatcher.match(charges, data.ledgerEntries()));
        groups.addAll(payoutBankMatcher.match(payouts, data.bankTransactions(), config.asOfDate()));

        // Fixed order (biggest money first), so "break #5" always means the same break for this seed
        List<ReconGroup> breaks = groups.stream()
                .filter(g -> g.outcome() == Outcome.BREAK)
                .sorted(Comparator.comparingLong(ReconGroup::amountImpact).reversed()
                        .thenComparing(g -> g.items().get(0).source())
                        .thenComparingLong(g -> g.items().get(0).recordId()))
                .toList();

        AccuracyReport accuracy = accuracyChecker.check(groups, data.processorTransactions(),
                data.ledgerEntries(), data.bankTransactions(), planted.answerKey());

        Map<Long, ProcessorTransaction> processorById = new HashMap<>();
        data.processorTransactions().forEach(p -> processorById.put(p.id(), p));
        Map<Long, LedgerEntry> ledgerById = new HashMap<>();
        data.ledgerEntries().forEach(l -> ledgerById.put(l.id(), l));
        Map<Long, BankTransaction> bankById = new HashMap<>();
        data.bankTransactions().forEach(b -> bankById.put(b.id(), b));

        return new Reconciled(groups, breaks, accuracy, processorById, ledgerById, bankById);
    }

    // =========================================================
    // Part 4: safety helpers
    // =========================================================

    /** The database normally assigns ids. In memory, we number the records ourselves. */
    private static GeneratedDataset withIds(GeneratedDataset d) {
        List<ProcessorTransaction> processor = new ArrayList<>();
        long id = 1;
        for (ProcessorTransaction t : d.processorTransactions()) {
            processor.add(new ProcessorTransaction(id++, t.externalId(), t.type(), t.grossAmount(), t.feeAmount(),
                    t.netAmount(), t.currency(), t.payoutExternalId(), t.description(), t.createdAt()));
        }
        List<LedgerEntry> ledger = new ArrayList<>();
        id = 1;
        for (LedgerEntry e : d.ledgerEntries()) {
            ledger.add(new LedgerEntry(id++, e.externalId(), e.processorRef(), e.amount(), e.currency(),
                    e.description(), e.entryDate()));
        }
        List<BankTransaction> bank = new ArrayList<>();
        id = 1;
        for (BankTransaction b : d.bankTransactions()) {
            bank.add(new BankTransaction(id++, b.externalId(), b.description(), b.amount(), b.currency(),
                    b.postedDate()));
        }
        return new GeneratedDataset(processor, ledger, bank);
    }

    /** Counts AI calls per day. Throws 429 Too Many Requests once the limit is reached. */
    private synchronized void useOneAiCall() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(counterDate)) {
            counterDate = today;
            aiCallsToday = 0;
        }
        if (aiCallsToday >= dailyAiLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Today's AI explanations are used up. Try again tomorrow.");
        }
        aiCallsToday++;
    }

    private synchronized int aiCallsLeftToday() {
        return Math.max(0, dailyAiLimit - aiCallsToday);
    }

    private SandboxExplanationResponse toResponse(BreakExplanation e) {
        return new SandboxExplanationResponse(e.likelyCause(),
                e.confidence() == null ? null : e.confidence().name(),
                e.suggestedFix(), aiCallsLeftToday());
    }
}