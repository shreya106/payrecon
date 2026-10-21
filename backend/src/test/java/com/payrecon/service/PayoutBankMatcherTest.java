package com.payrecon.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.payrecon.model.BankTransaction;
import com.payrecon.model.BreakType;
import com.payrecon.model.Outcome;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ProcessorTransactionType;
import com.payrecon.model.ReconGroup;

public class PayoutBankMatcherTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 15);

    private final PayoutBankMatcher matcher = new PayoutBankMatcher();

    @Test
    void payoutMatchesDepositWithSameAmountInWindow(){

        List<ProcessorTransaction> payouts = List.of(payout(1,"po_1",1000,LocalDate.of(2026, 10, 5)));
        List<BankTransaction> bankRows = List.of(bank(101,"STRIPE TRANSFER",1000,LocalDate.of(2026, 10, 7)));

        List<ReconGroup> groups = matcher.match(payouts, bankRows, AS_OF);

        assertEquals(1,groups.size());
        assertEquals(Outcome.MATCHED, groups.get(0).outcome());
        assertEquals(2, groups.get(0).items().size());
    }

    @Test
    void recentPayoutWithNoDepositIsInTransit(){
        List<ProcessorTransaction> payouts = List.of(payout(1,"po_1",1000,LocalDate.of(2026, 10, 14)));
        List<BankTransaction> bankRows = List.of();
        List<ReconGroup> groups = matcher.match(payouts, bankRows, AS_OF);

        assertEquals(1, groups.size());
        assertEquals(Outcome.BREAK, groups.get(0).outcome());
        assertEquals(BreakType.IN_TRANSIT, groups.get(0).breakType());
        assertEquals(1000, groups.get(0).amountImpact());
    
    }
    
    @Test 
    void overduePayoutWithNoDepositIsMissingInBank(){
        List<ProcessorTransaction> payouts = List.of(payout(1,"po_1",1000,LocalDate.of(2026, 10, 1)));
        List<BankTransaction> bankRows = List.of();

        List<ReconGroup> groups = matcher.match(payouts, bankRows, AS_OF);

        assertEquals(1, groups.size());
        assertEquals(Outcome.BREAK, groups.get(0).outcome());
        assertEquals(BreakType.MISSING_IN_BANK, groups.get(0).breakType());
        assertEquals(1000, groups.get(0).amountImpact());

    }

    @Test 
    void depositOutsideWindowDoesNotMatch(){
        List<ProcessorTransaction> payouts = List.of(payout(1,"po_1",1000,LocalDate.of(2026, 10, 1)));
        List<BankTransaction> bankRows = List.of(bank(101,"STRIPE TRANSFER",1000,LocalDate.of(2026, 10, 12)));

        List<ReconGroup> groups = matcher.match(payouts, bankRows, AS_OF);

        assertEquals(2, groups.size());
        assertEquals(Outcome.BREAK, groups.get(0).outcome());
        assertEquals(BreakType.MISSING_IN_BANK, groups.get(0).breakType());
        assertEquals(BreakType.UNEXPLAINED, groups.get(1).breakType());
       


    }

    @Test 
    void nonStripeBankRowsAreIgnored(){
        List<BankTransaction> bankRows = List.of(bank(101,"PAYROLL",-50000,LocalDate.of(2026, 10, 12)),
                                                 bank(102,"RENT",-30000,LocalDate.of(2026, 10, 12)));
        List<ProcessorTransaction> payouts = List.of();

        List<ReconGroup> groups = matcher.match(payouts, bankRows, AS_OF);

        assertEquals(0, groups.size());

    }

    @Test 
    void oneDepositCannotMatchTwoPayouts(){
        List<ProcessorTransaction> payouts = List.of(payout(1,"po_1",1000,LocalDate.of(2026, 10,5)),
        payout(2,"po_2",1000,LocalDate.of(2026, 10,5)));

        List<BankTransaction> bankRows = List.of(bank(101,"STRIPE TRANSFER",1000,LocalDate.of(2026, 10, 7)));
        List<ReconGroup> groups = matcher.match(payouts, bankRows, AS_OF);


        assertEquals(2, groups.size());
        assertEquals(Outcome.BREAK, groups.get(1).outcome());
        assertEquals(BreakType.MISSING_IN_BANK, groups.get(1).breakType());
        assertEquals(Outcome.MATCHED, groups.get(0).outcome());

    }
    
    


    private static ProcessorTransaction payout(long id, String externalId, long amount, LocalDate date) {
    return new ProcessorTransaction(id, externalId, ProcessorTransactionType.PAYOUT,
            amount, 0, amount, "USD", null, "test payout",
            date.atTime(23, 59).toInstant(ZoneOffset.UTC));
}

private static BankTransaction bank(long id, String description, long amount, LocalDate date) {
    return new BankTransaction(id, "B" + id, description, amount, "USD", date);
}
    
}
