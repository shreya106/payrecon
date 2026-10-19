package com.payrecon.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import com.payrecon.config.GeneratorConfig;
import com.payrecon.model.BankTransaction;
import com.payrecon.model.GeneratedDataset;
import com.payrecon.model.LedgerEntry;
import com.payrecon.model.ProcessorTransaction;
import com.payrecon.model.ProcessorTransactionType;
import com.payrecon.util.BusinessDays;

@Service
public class DataGenerator {

    private static final String CURRENCY = "USD";

    private static final List<String> CUSTOMERS = List.of(
            "Asha", "Ben", "Carlos", "Dana", "Eli",
            "Fatima", "George", "Hana", "Ivan", "Jia");

    private static final List<String> PRODUCTS = List.of(
            "Coffee subscription", "Espresso beans", "Cold brew pack",
            "Gift card", "Mug set");

    private final GeneratorConfig config;

    public DataGenerator(GeneratorConfig config) {
        this.config = config;
    }

    public GeneratedDataset generate(Random random) {
        List<ProcessorTransaction> processor = new ArrayList<>();
        List<LedgerEntry> ledger = new ArrayList<>();
        List<BankTransaction> bank = new ArrayList<>();

        LocalDate startDate = config.asOfDate().minusDays(config.days() - 1);
        int chargeCounter = 0;
        int bankCounter = 0;

        for (int dayIndex = 0; dayIndex < config.days(); dayIndex++) {
            LocalDate day = startDate.plusDays(dayIndex);
            String payoutId = String.format("po_%04d", dayIndex + 1);
            long payoutTotal = 0;

            // 1. Today's sales: one Stripe charge + one matching ledger entry each
            for (int i = 0; i < config.salesPerDay(); i++) {
                chargeCounter++;
                String chargeId = String.format("ch_%06d", chargeCounter);

                long gross = 500 + random.nextInt(19_501);   // $5.00 to $200.00, in cents
                long fee = stripeFee(gross);
                long net = gross - fee;

                Instant createdAt = randomTimeOn(day, random);
                String description = PRODUCTS.get(random.nextInt(PRODUCTS.size()))
                        + " - " + CUSTOMERS.get(random.nextInt(CUSTOMERS.size()));

                processor.add(new ProcessorTransaction(
                        null, chargeId, ProcessorTransactionType.CHARGE,
                        gross, fee, net, CURRENCY, payoutId, description, createdAt));

                ledger.add(new LedgerEntry(
                        null, String.format("L%06d", chargeCounter), chargeId,
                        gross, CURRENCY, description, createdAt.plusSeconds(2)));

                payoutTotal += net;
            }

            // 2. End of day: Stripe bundles today's sales into one payout
            Instant payoutTime = day.atTime(23, 59).toInstant(ZoneOffset.UTC);
            processor.add(new ProcessorTransaction(
                    null, payoutId, ProcessorTransactionType.PAYOUT,
                    payoutTotal, 0, payoutTotal, CURRENCY, null,
                    "Daily payout for " + day, payoutTime));

            // 3. The payout reaches the bank some business days later,
            //    but only if that date is not in the future
            LocalDate arrivalDate = BusinessDays.add(day, config.payoutDelayBusinessDays());
            if (!arrivalDate.isAfter(config.asOfDate())) {
                bankCounter++;
                bank.add(new BankTransaction(
                        null, String.format("B%05d", bankCounter),
                        "STRIPE TRANSFER", payoutTotal, CURRENCY, arrivalDate));
            }

            // 4. Unrelated bank rows the engine must ignore (money going out)
            if (day.getDayOfWeek() == DayOfWeek.FRIDAY) {
                bankCounter++;
                long payroll = 400_000 + random.nextInt(200_001);   // $4,000 to $6,000
                bank.add(new BankTransaction(
                        null, String.format("B%05d", bankCounter),
                        "PAYROLL", -payroll, CURRENCY, day));
            }
            if (day.getDayOfMonth() == 1) {
                bankCounter++;
                bank.add(new BankTransaction(
                        null, String.format("B%05d", bankCounter),
                        "RENT PAYMENT", -350_000, CURRENCY, day));
            }
        }

        return new GeneratedDataset(processor, ledger, bank);
    }

    /** Stripe fee: 2.9% + 30 cents, rounded to the nearest cent, using only whole numbers. */
    private static long stripeFee(long grossCents) {
        return (grossCents * 29 + 500) / 1000 + 30;
    }

    /** A random moment on the given day, before 23:59 (when the payout happens). */
    private static Instant randomTimeOn(LocalDate day, Random random) {
        return day.atStartOfDay().toInstant(ZoneOffset.UTC)
                .plusSeconds(random.nextInt(86_340));
    }

   
}