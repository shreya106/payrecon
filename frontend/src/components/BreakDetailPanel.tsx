import { useEffect, useState } from 'react'
import { api } from '../api'
import { BREAK_LABELS, formatCents } from '../format'
import type { BreakDetail, Confidence, RecordView } from '../types'

const SOURCE_LABELS: Record<RecordView['source'], string> = {
  PROCESSOR: 'Stripe',
  LEDGER: 'Ledger',
  BANK: 'Bank',
}
const SOURCE_COLORS: Record<RecordView['source'], string> = {
    PROCESSOR: 'bg-stripe',
    LEDGER: 'bg-ledger',
    BANK: 'bg-bank',
  }

const CONFIDENCE_TEXT: Record<Confidence, string> = {
  HIGH: 'High confidence',
  MEDIUM: 'Medium confidence',
  LOW: 'Low confidence',
}

function amountText(r: RecordView): string {
  if (r.source === 'PROCESSOR' && r.type === 'PAYOUT') {
    return `${formatCents(r.netAmountCents)} sent to the bank`
  }
  if (r.source === 'PROCESSOR') {
    return `${formatCents(r.grossAmountCents)} paid, ${formatCents(r.feeAmountCents)} fee, ${formatCents(r.netAmountCents)} net`
  }
  if (r.source === 'LEDGER') {
    return `${formatCents(r.amountCents)}, recorded for ${r.processorRef ?? 'no Stripe charge'}`
  }
  return `${formatCents(r.amountCents)} deposited`
}

type Props = { groupId: number; onClose: () => void }

type Result = { id: number; detail?: BreakDetail; error?: string }

export function BreakDetailPanel({ groupId, onClose }: Props) {
  const [result, setResult] = useState<Result | null>(null)

  useEffect(() => {
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

  const box = 'rounded-md border border-rule bg-white p-5 text-sm'

  if (result?.id !== groupId) return <aside className={`${box} text-muted`}>Loading…</aside>
  if (result.error || !result.detail) {
    return <aside className={`${box} text-risk`}>{result.error ?? 'Not found'}</aside>
  }

  const d = result.detail

  return (
    <aside className={`${box} space-y-5`}>
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="font-serif text-2xl">{d.breakType ? BREAK_LABELS[d.breakType] : 'Matched'}</h2>
          <p className="text-muted">
            {formatCents(d.amountImpactCents)} affected, break #{d.groupId}
          </p>
        </div>
        <button className="rounded-md px-2 py-1 text-muted hover:bg-paper" onClick={onClose}>
          Close
        </button>
      </div>

      {d.explanation ? (
        <div className="space-y-3">
          <p className="text-base leading-relaxed">{d.explanation.cause}</p>
          {d.explanation.suggestedFix && (
            <p className="leading-relaxed">
              <span className="font-semibold">Suggested fix: </span>
              {d.explanation.suggestedFix}
            </p>
          )}
          <p className="text-xs text-muted">
            {d.explanation.confidence ? `${CONFIDENCE_TEXT[d.explanation.confidence]}. ` : ''}
            Written by AI as advice; it never changes data.
          </p>
        </div>
      ) : (
        <p className="text-muted">No AI explanation yet.</p>
      )}

      <div>
        <h3 className="mb-2 font-semibold">The records</h3>
        <ul className="space-y-2">
          {d.records.map((r) => (
            <li key={`${r.source}-${r.recordId}`} className="rounded-md bg-paper p-3">
              <p className="flex items-center gap-2">
                <span className={`h-2.5 w-2.5 rounded-full ${SOURCE_COLORS[r.source]}`} />
                <span className="font-semibold">{SOURCE_LABELS[r.source]}</span> {r.externalId}
              </p>
              <p>{amountText(r)}</p>
              <p className="text-xs text-muted">
                {r.date?.slice(0, 16)}
                {r.description ? `, ${r.description}` : ''}
              </p>
            </li>
          ))}
        </ul>
      </div>
    </aside>
  )
}