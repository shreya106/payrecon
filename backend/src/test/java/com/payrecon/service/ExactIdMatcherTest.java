package com.payrecon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.payrecon.model.BreakType;
import com.payrecon.model.GroupItem;
import com.payrecon.model.LedgerEntry;
import com.payrecon.model.MatchMethod;
import com.payrecon.model.Outcome;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ProcessorTransactionType;
import com.payrecon.model.ReconGroup;
import com.payrecon.model.Source;

class ExactIdMatcherTest {

    private final ExactIdMatcher matcher = new ExactIdMatcher();

    // ---------------------------------------------------------------
    // The tiny example
    //   Charges: ch_1 5000, ch_2 2000, ch_3 3000, ch_4 1000
    //   Ledger:  L1 -> ch_1 5000          (match)
    //            L2, L3 -> ch_2 2000      (duplicate)
    //            L4 -> ch_3 2700          (amount mismatch)
    //            nothing -> ch_4          (missing in ledger)
    //            L9 -> ch_99 4000         (missing in processor)
    // ---------------------------------------------------------------

    private static List<ProcessorTransaction> tinyCharges() {
        return List.of(
                charge(1, "ch_1", 5000),
                charge(2, "ch_2", 2000),
                charge(3, "ch_3", 3000),
                charge(4, "ch_4", 1000));
    }

    private static List<LedgerEntry> tinyLedger() {
        return List.of(
                ledger(11, "L1", "ch_1", 5000),
                ledger(12, "L2", "ch_2", 2000),
                ledger(13, "L3", "ch_2", 2000),
                ledger(14, "L4", "ch_3", 2700),
                ledger(19, "L9", "ch_99", 4000));
    }

    @Test
    void classifiesEveryCaseInTheTinyExample() {
        // Act
        List<ReconGroup> groups = matcher.match(tinyCharges(), tinyLedger());

        // Assert
        assertEquals(5, groups.size());

        ReconGroup matched = groupContaining(groups, Source.PROCESSOR, 1);
        assertEquals(Outcome.MATCHED, matched.outcome());
        assertNull(matched.breakType());
        assertEquals(0, matched.amountImpact());
        assertEquals(2, matched.items().size());

        ReconGroup duplicate = groupContaining(groups, Source.PROCESSOR, 2);
        assertEquals(BreakType.DUPLICATE, duplicate.breakType());
        assertEquals(2000, duplicate.amountImpact());   // only the extra copy
        assertEquals(3, duplicate.items().size());      // charge + 2 ledger entries

        ReconGroup mismatch = groupContaining(groups, Source.PROCESSOR, 3);
        assertEquals(BreakType.AMOUNT_MISMATCH, mismatch.breakType());
        assertEquals(300, mismatch.amountImpact());     // |2700 - 3000|

        ReconGroup missingInLedger = groupContaining(groups, Source.PROCESSOR, 4);
        assertEquals(BreakType.MISSING_IN_LEDGER, missingInLedger.breakType());
        assertEquals(1000, missingInLedger.amountImpact());
        assertEquals(1, missingInLedger.items().size()); // the charge alone

        ReconGroup missingInProcessor = groupContaining(groups, Source.LEDGER, 19);
        assertEquals(BreakType.MISSING_IN_PROCESSOR, missingInProcessor.breakType());
        assertEquals(4000, missingInProcessor.amountImpact());

        for (ReconGroup g : groups) {
            assertEquals(MatchMethod.EXACT_ID, g.matchMethod());
        }
    }

    @Test
    void everyRecordEndsUpInExactlyOneGroup() {
        List<ProcessorTransaction> charges = tinyCharges();
        List<LedgerEntry> ledger = tinyLedger();

        List<ReconGroup> groups = matcher.match(charges, ledger);

        Set<GroupItem> seen = new HashSet<>();
        int totalItems = 0;
        for (ReconGroup g : groups) {
            for (GroupItem item : g.items()) {
                totalItems++;
                assertTrue(seen.add(item), "Record appears in two groups: " + item);
            }
        }
        assertEquals(charges.size() + ledger.size(), totalItems, "Some record was skipped");
    }

    @Test
    void noInputGivesNoGroups() {
        assertTrue(matcher.match(List.of(), List.of()).isEmpty());
    }

    @Test
    void ledgerEntryWithoutProcessorRefIsMissingInProcessor() {
        List<ReconGroup> groups = matcher.match(
                List.of(),
                List.of(ledger(21, "L21", null, 1500)));   // e.g. a manual entry with no Stripe id

        assertEquals(1, groups.size());
        assertEquals(BreakType.MISSING_IN_PROCESSOR, groups.get(0).breakType());
        assertEquals(1500, groups.get(0).amountImpact());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static ProcessorTransaction charge(long id, String externalId, long gross) {
        long fee = 30;   // the fee doesn't matter for Pass 1
        return new ProcessorTransaction(id, externalId, ProcessorTransactionType.CHARGE,
                gross, fee, gross - fee, "USD", "po_test", "test charge", Instant.EPOCH);
    }

    private static LedgerEntry ledger(long id, String externalId, String processorRef, long amount) {
        return new LedgerEntry(id, externalId, processorRef, amount, "USD", "test entry", Instant.EPOCH);
    }

    private static ReconGroup groupContaining(List<ReconGroup> groups, Source source, long recordId) {
        return groups.stream()
                .filter(g -> g.items().contains(new GroupItem(source, recordId)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No group contains " + source + " #" + recordId));
    }
}