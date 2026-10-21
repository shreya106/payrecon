import { useEffect, useState } from 'react'
import { api } from '../api'
import { BREAK_LABELS, BREAK_TYPES, formatCents } from '../format'
import type { BreakRow, BreakType, Page } from '../types'

const PAGE_SIZE = 15

type Props = {
  runId: number
  counts: Partial<Record<BreakType, number>>
  selectedId: number | null
  onSelect: (groupId: number | null) => void
}

type Result = { key: string; page?: Page<BreakRow>; error?: string }

export function BreaksTable({ runId, counts, selectedId, onSelect }: Props) {
  const [type, setType] = useState<BreakType | ''>('')
  const [includeInTransit, setIncludeInTransit] = useState(false)
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Result | null>(null)

  // Identifies which data the screen is asking for right now
  const requestKey = `${runId}|${type}|${includeInTransit}|${page}`

  useEffect(() => {
    let cancelled = false
    api
      .breaks(runId, {
        type: type || undefined,
        // Picking "In transit" in the dropdown should always show them
        includeInTransit: includeInTransit || type === 'IN_TRANSIT',
        page,
        size: PAGE_SIZE,
      })
      .then((data) => {
        if (!cancelled) setResult({ key: requestKey, page: data })
      })
      .catch((err: Error) => {
        if (!cancelled) setResult({ key: requestKey, error: err.message })
      })
    return () => {
      cancelled = true
    }
  }, [runId, type, includeInTransit, page, requestKey])

  const loading = result?.key !== requestKey
  const data = result?.page

  function goToPage(next: number) {
    setPage(next)
    onSelect(null)
  }

  return (
    <section>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <h2 className="font-serif text-2xl">Breaks</h2>

        <div className="flex flex-wrap items-center gap-4 text-sm text-muted">
          <label className="flex items-center gap-2">
            Type
            <select
              className="rounded-md border border-rule bg-white px-3 py-2 text-ink"
              value={type}
              onChange={(e) => {
                setType(e.target.value as BreakType | '')
                setPage(0)
                onSelect(null)
              }}
            >
              <option value="">All types</option>
              {BREAK_TYPES.map((t) => (
                <option key={t} value={t}>
                  {BREAK_LABELS[t]} ({counts[t] ?? 0})
                </option>
              ))}
            </select>
          </label>

          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={includeInTransit}
              onChange={(e) => {
                setIncludeInTransit(e.target.checked)
                setPage(0)
                onSelect(null)
              }}
            />
            Show in-transit (not at risk)
          </label>
        </div>
      </div>

      {result?.error && <p className="mb-3 text-risk">{result.error}</p>}

      <div className="overflow-hidden rounded-md border border-rule bg-white">
        <table className={`w-full table-fixed text-left text-sm ${loading ? 'opacity-50' : ''}`}>
          <thead className="text-muted">
            <tr className="border-b border-rule">
              <th className="w-44 px-4 py-3 font-medium">What’s wrong</th>
              <th className="w-32 px-4 py-3 text-right font-medium">Amount</th>
              <th className="px-4 py-3 font-medium">Likely cause</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((row) => {
              const selected = row.groupId === selectedId
              return (
                <tr
                  key={row.groupId}
                  tabIndex={0}
                  onClick={() => onSelect(row.groupId)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      onSelect(row.groupId)
                    }
                  }}
                  className={`cursor-pointer border-b border-rule last:border-0 focus-visible:outline-2 focus-visible:outline-link ${
                    selected ? 'bg-link/10' : 'hover:bg-paper'
                  }`}
                >
                  <td className="px-4 py-3">{BREAK_LABELS[row.breakType]}</td>
                  <td className="px-4 py-3 text-right">{formatCents(row.amountImpactCents)}</td>
                  <td className="truncate px-4 py-3 text-muted">{row.aiCause ?? 'Not explained yet'}</td>
                </tr>
              )
            })}
            {data && data.items.length === 0 && (
              <tr>
                <td colSpan={3} className="p-6 text-center text-muted">
                  No breaks of this kind.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {data && data.totalPages > 1 && (
        <div className="mt-3 flex items-center justify-between text-sm text-muted">
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <button
              className="rounded-md border border-rule bg-white px-3 py-1.5 text-ink disabled:opacity-40"
              disabled={page === 0}
              onClick={() => goToPage(page - 1)}
            >
              Previous
            </button>
            <button
              className="rounded-md border border-rule bg-white px-3 py-1.5 text-ink disabled:opacity-40"
              disabled={page + 1 >= data.totalPages}
              onClick={() => goToPage(page + 1)}
            >
              Next
            </button>
          </div>
        </div>
      )}
    </section>
  )
}