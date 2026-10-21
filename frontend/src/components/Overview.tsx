import { formatCents } from '../format'
import type { BreakType, RunSummary } from '../types'

const LEDGER_SIDE: BreakType[] = ['MISSING_IN_LEDGER', 'MISSING_IN_PROCESSOR', 'DUPLICATE', 'AMOUNT_MISMATCH']
const BANK_SIDE: BreakType[] = ['MISSING_IN_BANK', 'UNEXPLAINED']

function countOf(summary: RunSummary, types: BreakType[]): number {
  return types.reduce((sum, t) => sum + (summary.breaksByType[t] ?? 0), 0)
}



export function Hero({ summary }: { summary: RunSummary | null }) {
  return (
    <section className="mx-auto max-w-6xl px-6 pb-14 pt-8">
      {summary ? (
        <>
          <h2 className="max-w-3xl font-serif text-4xl leading-tight md:text-5xl">
            {summary.totalGroups.toLocaleString()} transactions checked across three systems.{' '}
            {summary.breaks} didn’t add up.
          </h2>
          <p className="mt-5 max-w-2xl text-lg text-hero-muted">
            <span className="font-semibold text-coral">{formatCents(summary.amountAtRiskCents)}</span> is at risk.
            Scroll down to see where, and why.
          </p>
          {summary.accuracy && (
            <p className="mt-5 inline-flex items-center gap-2 rounded-full bg-mint/15 px-4 py-1.5 text-sm text-mint">
              ✓ Verified on test data: caught {summary.accuracy.detected} of {summary.accuracy.seededBreaks} planted
              breaks, with {summary.accuracy.falseAlarms} false alarms
            </p>
          )}
        </>
      ) : (
        <h2 className="max-w-3xl font-serif text-4xl leading-tight md:text-5xl">
          Check that Stripe, the company’s books, and the bank all agree.
        </h2>
      )}
    </section>
  )
}

/* ---------- Part 2: how the money moves, with the traveling coin ---------- */

const STEPS = [
  { title: 'A customer pays', text: 'For example, $50 by card.', color: 'bg-ink' },
  { title: 'Stripe takes the payment', text: 'And keeps a small fee.', color: 'bg-stripe' },
  { title: 'The company records the sale', text: 'In its own books, called the ledger.', color: 'bg-ledger' },
  { title: 'Stripe sends the money to the bank', text: 'Once a day, about two business days later.', color: 'bg-bank' },
]

export function MoneyFlow() {
  return (
    <section className="py-12">
      <h3 className="mb-6 font-serif text-2xl">How the money moves</h3>
      <div className="relative">
        {/* The line behind the step circles, with the coin on it */}
        <div
          className="absolute top-[13px] hidden h-0.5 bg-rule md:block"
          style={{ left: '14px', right: 'calc(25% - 1.125rem - 14px)' }}
          aria-hidden="true"
        >
          <span className="coin" />
        </div>

        <ol className="relative grid gap-6 md:grid-cols-4">
          {STEPS.map((step, i) => (
            <li key={step.title}>
              <span
                className={`flex h-7 w-7 items-center justify-center rounded-full text-sm font-semibold text-white ${step.color}`}
              >
                {i + 1}
              </span>
              <p className="mt-3 font-semibold leading-snug">{step.title}</p>
              <p className="text-sm text-muted">{step.text}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}

/* ---------- Part 3: the two checks ---------- */

function Check(props: {
  accent: string
  dots: string[]
  systems: string
  question: string
  result: string
  detail: string
}) {
  return (
    <div className={`rounded-md border border-t-4 border-rule bg-white p-5 ${props.accent}`}>
      <p className="flex items-center gap-2 text-sm text-muted">
        <span className="flex">
          {props.dots.map((dot, i) => (
            <span key={dot} className={`h-2.5 w-2.5 rounded-full ring-2 ring-white ${dot} ${i > 0 ? '-ml-1' : ''}`} />
          ))}
        </span>
        {props.systems}
      </p>
      <p className="mt-3 text-lg font-semibold">{props.question}</p>
      <p className="mt-1 text-2xl font-semibold text-risk">{props.result}</p>
      <p className="mt-2 text-sm text-muted">{props.detail}</p>
    </div>
  )
}

export function Checks({ summary }: { summary: RunSummary }) {
  const inTransit = summary.breaksByType.IN_TRANSIT ?? 0
  return (
    <section className="pb-12">
      <h3 className="mb-6 font-serif text-2xl">What PayRecon checks</h3>
      <div className="grid gap-4 md:grid-cols-2">
        <Check
          accent="border-t-ledger"
          dots={['bg-stripe', 'bg-ledger']}
          systems="Stripe and the company’s books"
          question="Do they agree on every sale?"
          result={`${countOf(summary, LEDGER_SIDE)} problems`}
          detail="Sales missing from the books, sales recorded twice, wrong amounts, and entries for payments that never happened."
        />
        <Check
          accent="border-t-bank"
          dots={['bg-stripe', 'bg-bank']}
          systems="Stripe and the bank"
          question="Did the money actually arrive?"
          result={`${countOf(summary, BANK_SIDE)} problems, ${inTransit} on the way`}
          detail="Payouts that never arrived, and deposits nobody can explain."
        />
      </div>
      <p className="mt-4 text-sm text-muted">For every problem, AI suggests a likely cause and a fix.</p>
    </section>
  )
}