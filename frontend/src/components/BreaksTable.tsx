import { useEffect, useState } from 'react'
import { api } from '../api'
import { BREAK_LABELS, BREAK_TYPES, CONFIDENCE_STYLES, formatCents } from '../format'
import type { BreakRow, BreakType, Page } from '../types'

const PAGE_SIZE = 20

type Props = {
    runId: number
    selectedId: number | null
    onSelect: (groupId: number | null) => void
  }

type Result = { key: string; page?: Page<BreakRow>; error?: string }

export function BreaksTable({ runId, selectedId, onSelect }: Props) {
    const [type, setType] = useState<BreakType | ''>('')
    const [includeInTransit, setIncludeInTransit] = useState(false)
    const [page, setPage] = useState(0)
    const [result, setResult] = useState<Result | null>(null)

    // Identifies "which data the screen is asking for right now"
    const requestKey = `${runId}|${type}|${includeInTransit}|${page}`

    useEffect(() => {
        let cancelled = false
        api
            .breaks(runId, {
                type: type || undefined,
                // Picking "In transit" in the filter should always show them
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

    return (
        <section className="overflow-hidden rounded-lg border border-gray-200 bg-white">
            <div className="flex flex-wrap items-center gap-4 border-b border-gray-200 p-3 text-sm">
                <label className="flex items-center gap-2">
                    Type
                    <select
                        className="rounded border border-gray-300 px-2 py-1"
                        value={type}
                        onChange={(e) => {
                            setType(e.target.value as BreakType | '')
                            setPage(0)
                            onSelect(null)
                        }}
                    >
                        <option value="">All types</option>
                        {BREAK_TYPES.map((t) => (
                            <option key={t} value={t}>{BREAK_LABELS[t]}</option>
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

            {result?.error && <p className="p-4 text-red-600">{result.error}</p>}

            <table className={`w-full table-fixed text-left text-sm ${loading ? 'opacity-50' : ''}`}>
                <thead className="bg-gray-50 text-gray-500">
                    <tr>
                        <th className="w-40 px-3 py-2">Type</th>
                        <th className="w-32 px-3 py-2 text-right">Amount</th>
                        <th className="w-28 px-3 py-2">Confidence</th>
                        <th className="px-3 py-2">AI cause</th>
                    </tr>
                </thead>
                <tbody>
                    {data?.items.map((row) => (
                        <tr
                            key={row.groupId}
                            onClick={() => onSelect(row.groupId)}
                            className={`cursor-pointer border-t border-gray-100 hover:bg-blue-50 ${row.groupId === selectedId ? 'bg-blue-100' : ''
                                }`}
                        >
                            <td className="px-3 py-2 whitespace-nowrap">{BREAK_LABELS[row.breakType]}</td>
                            <td className="px-3 py-2 text-right font-mono">{formatCents(row.amountImpactCents)}</td>
                            <td className="px-3 py-2">
                                {row.aiConfidence ? (
                                    <span className={`rounded px-2 py-0.5 text-xs ${CONFIDENCE_STYLES[row.aiConfidence]}`}>
                                        {row.aiConfidence}
                                    </span>
                                ) : (
                                    <span className="text-gray-400">—</span>
                                )}
                            </td>
                            <td className="truncate px-3 py-2 text-gray-600">{row.aiCause ?? 'Not explained yet'}</td>
                        </tr>
                    ))}
                    {data && data.items.length === 0 && (
                        <tr>
                            <td colSpan={4} className="p-4 text-center text-gray-500">No breaks match this filter.</td>
                        </tr>
                    )}
                </tbody>
            </table>

            {data && (
                <div className="flex items-center justify-between border-t border-gray-200 p-3 text-sm">
                    <span className="text-gray-500">
                        {data.totalItems} breaks · page {data.totalPages === 0 ? 0 : data.page + 1} of {data.totalPages}
                    </span>
                    <div className="flex gap-2">
                        <button
                            className="rounded border border-gray-300 px-3 py-1 disabled:opacity-40"
                            disabled={page === 0}
                            onClick={() => setPage((p) => p - 1)}
                        >
                            ‹ Prev
                        </button>
                        <button
                            className="rounded border border-gray-300 px-3 py-1 disabled:opacity-40"
                            disabled={page + 1 >= data.totalPages}
                            onClick={() => setPage((p) => p + 1)}
                        >
                            Next ›
                        </button>
                    </div>
                </div>
            )}
        </section>
    )
}