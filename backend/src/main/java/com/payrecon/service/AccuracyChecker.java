package com.payrecon.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.payrecon.model.AccuracyReport;
import com.payrecon.model.BankTransaction;
import com.payrecon.model.BreakType;
import com.payrecon.model.GroupItem;
import com.payrecon.model.LedgerEntry;
import com.payrecon.model.Outcome;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ReconGroup;
import com.payrecon.model.SeededBreak;
import com.payrecon.model.Source;
import com.payrecon.repository.SeededBreakRepository;

@Service
public class AccuracyChecker {

    private final SeededBreakRepository seededBreakRepository;

    public AccuracyChecker(SeededBreakRepository seededBreakRepository) {
        this.seededBreakRepository = seededBreakRepository;
    }

    public AccuracyReport check(List<ReconGroup> groups,
                                List<ProcessorTransaction> processorRows,
                                List<LedgerEntry> ledgerEntries,
                                List<BankTransaction> bankTransactions) {

        // 1. A lookup from "SOURCE#id" to external ID, e.g. "LEDGER#42" -> "L000042".
        //    Groups store our database ids; the answer key stores external IDs.
        Map<String, String> externalIdOf = new HashMap<>();
        for (ProcessorTransaction p : processorRows) {
            externalIdOf.put(Source.PROCESSOR + "#" + p.id(), p.externalId());
        }
        for (LedgerEntry l : ledgerEntries) {
            externalIdOf.put(Source.LEDGER + "#" + l.id(), l.externalId());
        }
        for (BankTransaction b : bankTransactions) {
            externalIdOf.put(Source.BANK + "#" + b.id(), b.externalId());
        }

        // 2. Describe every break the engine found as text, e.g. "DUPLICATE:L000567,L006001,ch_000567"
        Set<String> found = new HashSet<>();
        for (ReconGroup group : groups) {
            if (group.outcome() != Outcome.BREAK) {
                continue;
            }
            List<String> ids = new ArrayList<>();
            for (GroupItem item : group.items()) {
                ids.add(externalIdOf.get(item.source() + "#" + item.recordId()));
            }
            found.add(describe(group.breakType(), ids));
        }

        // 3. Describe every break in the answer key the same way
        Set<String> expected = new HashSet<>();
        for (SeededBreak seeded : seededBreakRepository.findAll()) {
            expected.add(describe(seeded.breakType(), seeded.externalIds()));
        }

        // 4. Compare the two sets
        int detected = 0;
        for (String e : expected) {
            if (found.contains(e)) {
                detected++;
            }
        }
        int falseAlarms = 0;
        for (String f : found) {
            if (!expected.contains(f)) {
                falseAlarms++;
            }
        }

        return new AccuracyReport(expected.size(), detected, expected.size() - detected, falseAlarms);
    }

    /** Same type + same IDs (in any order) gives the same text. */
    private static String describe(BreakType type, List<String> externalIds) {
        return type + ":" + externalIds.stream().sorted().collect(Collectors.joining(","));
    }
}