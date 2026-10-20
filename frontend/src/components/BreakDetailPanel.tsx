import { useEffect, useState } from 'react'
import { api } from '../api'
import { BREAK_LABELS, CONFIDENCE_STYLES, formatCents } from '../format'
import type { BreakDetail, RecordView } from '../types'

const SOURCE_LABELS: Record<RecordView['source'], string> = {
  PROCESSOR: 'Stripe',
  LEDGER: 'Ledger',
  BANK: 'Bank',
}

function amountText(r: RecordView): string {
  if (r.source === 'PROCESSOR') {
    return `gross ${formatCents(r.grossAmountCents)} · fee ${formatCents(r.feeAmountCents)} · net ${formatCents(r.netAmountCents)}`
  }
  if (r.source === 'LEDGER') {
    return `${formatCents(r.amountCents)} → ${r.processorRef ?? 'no Stripe reference'}`
  }
  return formatCents(r.amountCents)
}

type Props = { groupId: number | null }

type Result = { id: number; detail?: BreakDetail; error?: string }

export function BreakDetailPanel({ groupId }: Props) {
  const [result, setResult] = useState<Result | null>(null)

  useEffect(() => {
    if (groupId == null) return
    let cancelled = false
    api
      .breakDetail(groupId)
      .then((detail) => {
        if (!cancelled) setResult({ id: groupId, detail })
      })
      .catch((err: Error) => {
        if (!cancelled) setResult({ id: groupId, error: err.message })
      })
    return () => {
      cancelled = true
    }
  }, [groupId])

  const box = 'rounded-lg border border-gray-200 bg-white p-4 text-sm'

  if (groupId == null) {
    return <aside className={`${box} text-gray-500`}>Select a break to see its records and the AI explanation.</aside>
  }
  if (result?.id !== groupId) {
    return <aside className={`${box} text-gray-500`}>Loading…</aside>
  }
  if (result.error || !result.detail) {
    return <aside className={`${box} text-red-600`}>{result.error ?? 'Not found'}</aside>
  }

  const d = result.detail

  return (
    <aside className={`${box} space-y-4`}>
      <div>
        <p className="text-xs uppercase text-gray-500">Break #{d.groupId}</p>
        <h2 className="text-lg font-semibold">{d.breakType ? BREAK_LABELS[d.breakType] : 'Matched'}</h2>
        <p className="text-gray-600">Money affected: {formatCents(d.amountImpactCents)}</p>
      </div>

      <div>
        <h3 className="mb-2 font-medium">Records</h3>
        <ul className="space-y-2">
          {d.records.map((r) => (
            <li key={`${r.source}-${r.recordId}`} className="rounded border border-gray-100 bg-gray-50 p-2">
              <p>
                <span className="font-medium">{SOURCE_LABELS[r.source]}</span>{' '}
                <span className="font-mono">{r.externalId}</span>
                {r.type && <span className="text-gray-500"> · {r.type}</span>}
              </p>
              <p className="font-mono text-xs">{amountText(r)}</p>
              <p className="text-xs text-gray-500">
                {r.date?.slice(0, 16)}
                {r.description && ` · ${r.description}`}
              </p>
            </li>
          ))}
        </ul>
      </div>

      <div>
        <h3 className="mb-2 font-medium">AI explanation</h3>
        {d.explanation ? (
          <div className="space-y-2">
            {d.explanation.confidence && (
              <span className={`rounded px-2 py-0.5 text-xs ${CONFIDENCE_STYLES[d.explanation.confidence]}`}>
                {d.explanation.confidence} confidence
              </span>
            )}
            <p><strong>Likely cause:</strong> {d.explanation.cause}</p>
            {d.explanation.suggestedFix && (
              <p><strong>Suggested fix:</strong> {d.explanation.suggestedFix}</p>
            )}
            <p className="text-xs text-gray-400">AI-generated and advisory only. It never changes data.</p>
          </div>
        ) : (
          <p className="text-gray-500">Not explained yet.</p>
        )}
      </div>
    </aside>
  )
}