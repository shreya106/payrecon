package com.payrecon.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.payrecon.model.BreakType;
import com.payrecon.model.GroupItem;
import com.payrecon.model.LedgerEntry;
import com.payrecon.model.MatchMethod;
import com.payrecon.model.Outcome;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ReconGroup;
import com.payrecon.model.Source;

@Service
public class ExactIdMatcher {

    /**
     * Pass 1: match Stripe charges to ledger entries by processorRef.
     * Every charge and every ledger entry ends up in exactly one group.
     */
    public List<ReconGroup> match(List<ProcessorTransaction> charges,
            List<LedgerEntry> ledgerEntries) {
        List<ReconGroup> groups = new ArrayList<>();

        Map<String, List<LedgerEntry>> processorRefToLedgerEntries = new HashMap<>();

        for (LedgerEntry entry : ledgerEntries) {
            processorRefToLedgerEntries.computeIfAbsent(entry.processorRef(), x -> new ArrayList<>()).add(entry);
        }

        for (ProcessorTransaction charge : charges) {
            List<LedgerEntry> entry = processorRefToLedgerEntries.remove(charge.externalId());

            if (entry == null) {
                List<GroupItem> items = new ArrayList<>();
                items.add(new GroupItem(Source.PROCESSOR, charge.id()));
                ReconGroup rg = new ReconGroup(Outcome.BREAK, MatchMethod.EXACT_ID, BreakType.MISSING_IN_LEDGER,
                        charge.grossAmount(), items);

                groups.add(rg);

            } else if (entry.size() == 1) {
                List<GroupItem> items = new ArrayList<>();
                LedgerEntry ledgerEntry = entry.get(0);
                items.add(new GroupItem(Source.PROCESSOR, charge.id()));
                items.add(new GroupItem(Source.LEDGER, entry.get(0).id()));
                if (ledgerEntry.amount() == charge.grossAmount()) {

                    ReconGroup rg = new ReconGroup(Outcome.MATCHED, MatchMethod.EXACT_ID, null,
                            0, items);

                    groups.add(rg);
                } else {
                    ReconGroup rg = new ReconGroup(Outcome.BREAK, MatchMethod.EXACT_ID, BreakType.AMOUNT_MISMATCH,
                            Math.abs(ledgerEntry.amount() - charge.grossAmount()), items);

                    groups.add(rg);
                }
            } else {
                List<GroupItem> items = new ArrayList<>();
                items.add(new GroupItem(Source.PROCESSOR, charge.id()));

                for (LedgerEntry ledgerEntry : entry) {
                    items.add(new GroupItem(Source.LEDGER, ledgerEntry.id()));
                }
                long extraAmount = 0;
                for (int i = 1; i < entry.size(); i++) {
                    extraAmount += entry.get(i).amount();
                }
                ReconGroup rg = new ReconGroup(Outcome.BREAK, MatchMethod.EXACT_ID, BreakType.DUPLICATE,
                        extraAmount, items);
                groups.add(rg);

            }

        }

        for (List<LedgerEntry> leftOvers : processorRefToLedgerEntries.values()) {
            for (LedgerEntry ledgerEntry : leftOvers) {
                List<GroupItem> items = new ArrayList<>();
                items.add(new GroupItem(Source.LEDGER, ledgerEntry.id()));
                ReconGroup rg = new ReconGroup(Outcome.BREAK, MatchMethod.EXACT_ID, BreakType.MISSING_IN_PROCESSOR,
                        ledgerEntry.amount(), items);
                groups.add(rg);

            }
        }

        return groups;
    }
}