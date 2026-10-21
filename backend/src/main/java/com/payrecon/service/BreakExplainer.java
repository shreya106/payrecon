package com.payrecon.service;
import java.time.LocalDate;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.payrecon.model.BreakExplanation;
import com.payrecon.model.BreakExplanationBatch;
import com.payrecon.model.BreakForExplanation;

@Service
public class BreakExplainer {
  private static final Logger log = LoggerFactory.getLogger(BreakExplainer.class);

    private static final String SYSTEM_PROMPT = """
            You are a payments reconciliation analyst at a company that sells online and uses Stripe.
            Three sources should agree:
            - Stripe (processor): charges that customers pay, and daily payouts Stripe sends to the bank.
            - Ledger: the company's own books. Each entry should point to one Stripe charge
              (processor_ref) and record the same gross amount.
            - Bank: the company's bank statement. Each Stripe payout should arrive as one
              STRIPE TRANSFER deposit about 2 business days after the payout.
            All amounts are in cents (5000 means $50.00). Write money as dollars in your answers.
            Use the reconciliation date you are given as "today" to judge whether something is late.

            Break types:
            - MISSING_IN_LEDGER: a Stripe charge has no ledger entry.
            - MISSING_IN_PROCESSOR: a ledger entry points to a Stripe charge that does not exist.
            - DUPLICATE: one Stripe charge has two or more ledger entries.
            - AMOUNT_MISMATCH: a ledger amount differs from its Stripe charge.
            - IN_TRANSIT: a payout was sent recently and is not due at the bank yet.
            - MISSING_IN_BANK: a payout is overdue and no deposit arrived.
            - UNEXPLAINED: a STRIPE TRANSFER deposit matches no payout.

            For each break, give:
            - likelyCause: WHY this most likely happened (the root cause), in 1 or 2 plain sentences.
              Do not just restate the break type; the reader already knows what is wrong.
              Look for clues in the evidence, for example:
                - an amount difference that is an exact percentage (a discount or coupon)
                - entries a few seconds apart (a retried webhook)
                - a description like "Manual entry", or an ID far outside the normal range (manual error)
                - dates and business days (timing)
              Use only the evidence given. Do not invent facts.
            - confidence: how sure you are about the ROOT CAUSE.
              HIGH only if the evidence directly shows it, MEDIUM if it is a likely guess,
              LOW if it is unclear.
            - suggestedFix: one concrete next step for the finance team, in 1 or 2 sentences.
              If the cause points to a system bug, mention preventing it from happening again.
            Return exactly one explanation per break, using the same groupId you were given.
            """;

    private final ChatClient chatClient;

    public BreakExplainer(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    public List<BreakExplanation> explain(List<BreakForExplanation> breaks, LocalDate asOfDate) {
      StringBuilder text = new StringBuilder()
              .append("Reconciliation date (\"today\" for this data): ").append(asOfDate).append("\n\n")
              .append("Explain these breaks.\n\n");
      for (BreakForExplanation b : breaks) {
          text.append("groupId: ").append(b.groupId()).append('\n')
              .append("breakType: ").append(b.breakType()).append('\n')
              .append("amountImpact in cents: ").append(b.amountImpact()).append('\n')
              .append("records:\n");
          for (String line : b.evidence()) {
              text.append("  - ").append(line).append('\n');
          }
          text.append('\n');
      }
  
      // Tells Claude the exact JSON shape we want, and later turns the JSON into our record
      BeanOutputConverter<BreakExplanationBatch> converter =
              new BeanOutputConverter<>(BreakExplanationBatch.class);
  
      ChatResponse response = chatClient.prompt()
              .user(u -> u.text(text + "\n{format}").param("format", converter.getFormat()))
              .call()
              .chatResponse();
  
      if (response == null) {
          throw new IllegalStateException("AI returned no response");
      }
  
      // The reply can have several parts (e.g. the model's reasoning, then the answer).
      // Use the last part that actually contains text.
      String json = "";
      for (Generation part : response.getResults()) {
          String partText = part.getOutput().getText();
          log.debug("AI reply part: finishReason={}, length={}",
                  part.getMetadata().getFinishReason(),
                  partText == null ? 0 : partText.length());
          if (partText != null && !partText.isBlank()) {
              json = partText;
          }
      }
  
      if (json.isBlank()) {
          throw new IllegalStateException("AI reply contained no text");
      }
  
      BreakExplanationBatch result = converter.convert(json);
      if (result == null || result.explanations() == null) {
          return List.of();
      }
      return result.explanations();
  }
}