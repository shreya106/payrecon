import type { RunSummary } from '../types'
import { BREAK_LABELS, formatCents } from '../format'
import type { BreakType } from '../types'

type Props = { summary: RunSummary }

export function SummaryCards({ summary }: Props) {
  const matchRate = summary.totalGroups === 0
    ? 0
    : (summary.matched / summary.totalGroups) * 100

  const cards = [
    { label: 'Match rate', value: `${matchRate.toFixed(1)}%` },
    { label: 'Matched', value: summary.matched.toLocaleString() },
    { label: 'Breaks', value: summary.breaks.toLocaleString() },
    { label: 'Amount at risk', value: formatCents(summary.amountAtRiskCents), danger: true },
  ]

  return (
    <section className="space-y-3">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {cards.map((c) => (
          <div key={c.label} className="rounded-lg border border-gray-200 bg-white p-4">
            <p className="text-sm text-gray-500">{c.label}</p>
            <p className={`mt-1 text-2xl font-semibold ${c.danger ? 'text-red-700' : 'text-gray-900'}`}>
              {c.value}
            </p>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-2 text-sm">
        {(Object.entries(summary.breaksByType) as [BreakType, number][]).map(([type, count]) => (
          <span key={type} className="rounded-full bg-gray-100 px-3 py-1 text-gray-700">
            {BREAK_LABELS[type]}: {count}
          </span>
        ))}
      </div>

      {summary.accuracy && (
        <p className="text-sm text-gray-600">
          Accuracy check: detected <strong>{summary.accuracy.detected}</strong> of{' '}
          {summary.accuracy.seededBreaks} planted breaks · {summary.accuracy.missed} missed ·{' '}
          {summary.accuracy.falseAlarms} false alarms · AI explained {summary.explained} of {summary.breaks}
        </p>
      )}
    </section>
  )
}