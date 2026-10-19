package com.payrecon.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.payrecon.model.BankTransaction;
import com.payrecon.model.BreakType;
import com.payrecon.model.GroupItem;
import com.payrecon.model.MatchMethod;
import com.payrecon.model.Outcome;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ReconGroup;
import com.payrecon.model.Source;
import com.payrecon.util.BusinessDays;

public class PayoutBankMatcher {
    private static final String STRIPE_DEPOSIT = "STRIPE TRANSFER";
    private static final int MAX_DAYS_TO_ARRIVE = 5;
    private static final int EXPECTED_DELAY_BUSINESS_DAYS = 2;

    public List<ReconGroup> match(List<ProcessorTransaction> payouts,
            List<BankTransaction> bankTransactions, LocalDate asOfDate) {

        List<ReconGroup> groups = new ArrayList<>();

        Map<Long, List<BankTransaction>> depositByAmount = new HashMap<>();
        for (BankTransaction bank : bankTransactions) {
            if (STRIPE_DEPOSIT.equals(bank.description())) {
                depositByAmount.computeIfAbsent(bank.amount(), x -> new ArrayList<>()).add(bank);
            }

            for (ProcessorTransaction payout : payouts) {
                LocalDate payoutDate = LocalDate.ofInstant(payout.createdAt(), ZoneOffset.UTC);
                BankTransaction deposit = takeDeposit(depositByAmount, payout.netAmount(), payoutDate);

                if(deposit != null){
                    groups.add(new ReconGroup(
                        Outcome.MATCHED, MatchMethod.PAYOUT_BATCH, null, 0,
                        List.of(new GroupItem(Source.PROCESSOR, payout.id()),
                                new GroupItem(Source.BANK, deposit.id()))));
            }
            else{
                LocalDate expectedArrival = BusinessDays.add(payoutDate,EXPECTED_DELAY_BUSINESS_DAYS);
                BreakType type = expectedArrival.isAfter(asOfDate)?BreakType.IN_TRANSIT:BreakType.MISSING_IN_BANK;


                groups.add(new ReconGroup(
                    Outcome.BREAK, MatchMethod.PAYOUT_BATCH, type, payout.netAmount(),
                    List.of(new GroupItem(Source.PROCESSOR, payout.id()))));

            }
        }

        for (List<BankTransaction> leftovers : depositByAmount.values()) {
            for (BankTransaction deposit : leftovers) {
                groups.add(new ReconGroup(
                        Outcome.BREAK, MatchMethod.PAYOUT_BATCH, BreakType.UNEXPLAINED,
                        deposit.amount(),
                        List.of(new GroupItem(Source.BANK, deposit.id()))));
            }
        }

      
    }
    return groups;
}

    private BankTransaction takeDeposit(Map<Long, List<BankTransaction>> depositByAmount, long netAmount,
            LocalDate payoutDate) {
        List<BankTransaction> candidates = depositByAmount.get(netAmount);
        if (candidates == null)
            return null;

        Iterator<BankTransaction> it = candidates.iterator();
        while (it.hasNext()) {
            BankTransaction deposit = it.next();

            boolean onOrAfterPayout = !deposit.postedDate().isBefore(payoutDate);
            boolean withinWindow = !deposit.postedDate().isAfter(payoutDate.plusDays(MAX_DAYS_TO_ARRIVE));

            if (onOrAfterPayout && withinWindow) {
                it.remove();
                if (candidates.isEmpty()) {
                    depositByAmount.remove(netAmount);
                }
                return deposit;
            }
        }
        return null;
    }

}
