package com.payrecon.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import com.payrecon.config.GeneratorConfig;
import com.payrecon.model.BankTransaction;
import com.payrecon.model.BreakType;
import com.payrecon.model.GeneratedDataset;
import com.payrecon.model.LedgerEntry;
import com.payrecon.model.PlantResult;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ProcessorTransactionType;
import com.payrecon.model.SeededBreak;

@Service
public class BreakPlanter {

    private static final int BANK_LEVEL_EACH = 2;

    private final GeneratorConfig config;

    public BreakPlanter(GeneratorConfig config) {
        this.config = config;
    }

    public PlantResult plant(GeneratedDataset clean, Random random) {
        List<ProcessorTransaction> processor = clean.processorTransactions();
        List<LedgerEntry> ledger = new ArrayList<>(clean.ledgerEntries());
        List<BankTransaction> bank = new ArrayList<>(clean.bankTransactions());
        List<SeededBreak> answerKey = new ArrayList<>();

        int totalBreaks = (int) Math.round(clean.ledgerEntries().size() * config.breakRate());
        int saleLevelEach = (totalBreaks - 2 * BANK_LEVEL_EACH) / 4;

        int nextLedgerNumber = clean.ledgerEntries().size() + 1;
        int nextBankNumber = clean.bankTransactions().size() + 1;

        // Shuffle the sales once, then take them in order,
        // so no sale gets two mistakes.
        List<LedgerEntry> candidates = new ArrayList<>(clean.ledgerEntries());
        Collections.shuffle(candidates, random);
        int pick = 0;

        // 1. MISSING_IN_LEDGER: delete the ledger entry
        for (int i = 0; i < saleLevelEach; i++) {
            LedgerEntry entry = candidates.get(pick++);
            ledger.remove(entry);
            answerKey.add(new SeededBreak(BreakType.MISSING_IN_LEDGER,
                    List.of(entry.processorRef()),
                    "Ledger entry " + entry.externalId() + " never written (webhook lost)"));
        }

        // 2. DUPLICATE: add a second copy of the ledger entry
        for (int i = 0; i < saleLevelEach; i++) {
            LedgerEntry entry = candidates.get(pick++);
            LedgerEntry copy = new LedgerEntry(
                    null, String.format("L%06d", nextLedgerNumber++),
                    entry.processorRef(), entry.amount(), entry.currency(),
                    entry.description(), entry.entryDate().plusSeconds(30));
            ledger.add(copy);
            answerKey.add(new SeededBreak(BreakType.DUPLICATE,
                    List.of(entry.processorRef(), entry.externalId(), copy.externalId()),
                    "Webhook retried; ledger entry written twice"));
        }

        // 3. AMOUNT_MISMATCH: ledger records 10% less than Stripe
        for (int i = 0; i < saleLevelEach; i++) {
            LedgerEntry entry = candidates.get(pick++);
            long discounted = entry.amount() - entry.amount() / 10;
            LedgerEntry changed = new LedgerEntry(
                    entry.id(), entry.externalId(), entry.processorRef(),
                    discounted, entry.currency(), entry.description(), entry.entryDate());
            ledger.set(ledger.indexOf(entry), changed);
            answerKey.add(new SeededBreak(BreakType.AMOUNT_MISMATCH,
                    List.of(entry.processorRef(), entry.externalId()),
                    "Discount recorded in ledger only"));
        }

        // 4. MISSING_IN_PROCESSOR: a ledger entry pointing to a Stripe ID that doesn't exist
        for (int i = 0; i < saleLevelEach; i++) {
            LedgerEntry dateSource = candidates.get(pick++);
            String fakeChargeId = String.format("ch_%06d", 900_000 + i + 1);
            long amount = 500 + random.nextInt(19_501);
            LedgerEntry orphan = new LedgerEntry(
                    null, String.format("L%06d", nextLedgerNumber++),
                    fakeChargeId, amount, "USD",
                    "Manual entry - unknown customer", dateSource.entryDate());
            ledger.add(orphan);
            answerKey.add(new SeededBreak(BreakType.MISSING_IN_PROCESSOR,
                    List.of(orphan.externalId()),
                    "Points to " + fakeChargeId + ", which Stripe never created"));
        }

        // 5. IN_TRANSIT: payouts with no bank deposit yet (these happened naturally)
        List<ProcessorTransaction> arrivedPayouts = new ArrayList<>();
        for (ProcessorTransaction payout : processor) {
            if (payout.type() != ProcessorTransactionType.PAYOUT) {
                continue;
            }
            boolean inBank = bank.stream().anyMatch(b -> b.amount() == payout.netAmount());
            if (inBank) {
                arrivedPayouts.add(payout);
            } else {
                answerKey.add(new SeededBreak(BreakType.IN_TRANSIT,
                        List.of(payout.externalId()),
                        "Payout sent, not yet posted by the bank"));
            }
        }

        // 6. MISSING_IN_BANK: delete the deposit of a few payouts older than a week
        Instant weekAgo = config.asOfDate().minusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<ProcessorTransaction> oldPayouts = new ArrayList<>(arrivedPayouts.stream()
                .filter(p -> p.createdAt().isBefore(weekAgo))
                .toList());
        Collections.shuffle(oldPayouts, random);
        for (int i = 0; i < BANK_LEVEL_EACH; i++) {
            ProcessorTransaction payout = oldPayouts.get(i);
            bank.removeIf(b -> b.amount() == payout.netAmount());
            answerKey.add(new SeededBreak(BreakType.MISSING_IN_BANK,
                    List.of(payout.externalId()),
                    "Payout never arrived (e.g. rejected by the bank)"));
        }

        // 7. UNEXPLAINED: a "STRIPE TRANSFER" deposit that matches no payout
        for (int i = 0; i < BANK_LEVEL_EACH; i++) {
            long amount = 10_000 + random.nextInt(90_001);   // $100 to $1,000
            LocalDate date = config.asOfDate().minusDays(3 + random.nextInt(20));
            BankTransaction mystery = new BankTransaction(
                    null, String.format("B%05d", nextBankNumber++),
                    "STRIPE TRANSFER", amount, "USD", date);
            bank.add(mystery);
            answerKey.add(new SeededBreak(BreakType.UNEXPLAINED,
                    List.of(mystery.externalId()),
                    "Deposit labeled STRIPE TRANSFER, matches no payout"));
        }

        return new PlantResult(new GeneratedDataset(processor, ledger, bank), answerKey);
    }
}