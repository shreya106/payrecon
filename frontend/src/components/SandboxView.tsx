import { useState } from 'react'
import { ApiError, api } from '../api'
import { BREAK_LABELS, BREAK_TYPES, formatCents } from '../format'
import type { BreakType, Confidence, SandboxExplanation, SandboxRun } from '../types'

const PAGE_SIZE = 15

const CONFIDENCE_TEXT: Record<Confidence, string> = {
  HIGH: 'High confidence',
  MEDIUM: 'Medium confidence',
  LOW: 'Low confidence',
}

export function SandboxView() {
  const [run, setRun] = useState<SandboxRun | null>(null)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [type, setType] = useState<BreakType | ''>('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)

  // AI answers we already got, by break index, so reopening a break shows it instantly
  const [explanations, setExplanations] = useState<Record<number, SandboxExplanation>>({})
  const [explaining, setExplaining] = useState(false)
  const [explainError, setExplainError] = useState<string | null>(null)
  const [aiLeft, setAiLeft] = useState<number | null>(null)

  async function generate() {
    setGenerating(true)
    setError(null)
    try {
      const result = await api.sandboxRun()
      setRun(result)
      setType('')
      setPage(0)
      setSelected(null)
      setExplanations({})
      setExplainError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setGenerating(false)
    }
  }

  async function explain(index: number) {
    if (!run) return
    setExplaining(true)
    setExplainError(null)
    try {
      const result = await api.sandboxExplain(run.seed, index)
      setExplanations((prev) => ({ ...prev, [index]: result }))
      setAiLeft(result.aiExplanationsLeftToday)
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        setExplainError(
          'Today’s AI explanations are used up. Try again tomorrow, or open the sample month, where every break is already explained.',
        )
      } else {
        setExplainError('The AI couldn’t explain this break right now. Please try again.')
      }
    } finally {
      setExplaining(false)
    }
  }

  // Filtering and paging happen here in the browser: the whole month is already loaded
  const filtered = run ? run.breakList.filter((b) => type === '' || b.breakType === type) : []
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const pageItems = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const selectedBreak = run && selected != null ? run.breakList[selected] : null
  const explanation = selected != null ? explanations[selected] : undefined

  return (
    <section className="space-y-6">
      {/* The generate box */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-md border border-rule bg-white p-5">
        <div className="max-w-xl">
          <p className="font-semibold">Generate your own month of payments</p>
          <p className="text-sm text-muted">
            Create a fresh month of about 6,000 sales with a different set of hidden mistakes. PayRecon reconciles
            it in about a second. Then pick any break to see what went wrong, and ask the AI why.
          </p>
        </div>
        <button
          onClick={generate}
          disabled={generating}
          className="rounded-md bg-hero px-4 py-2 text-sm font-semibold text-hero-ink hover:brightness-125 disabled:opacity-50"
        >
          {generating ? 'Generating…' : run ? 'Generate another month' : 'Generate a new month'}
        </button>
      </div>

      {error && <p className="text-risk">Couldn’t generate a month: {error}</p>}

      {run && (
        <>
          {/* Summary of this month */}
          <div>
            <p className="font-serif text-2xl">
              {run.breaks} breaks found in {run.totalGroups.toLocaleString()} transactions.
            </p>
            <p className="mt-1 text-muted">
              <span className="font-semibold text-risk">{formatCents(run.amountAtRiskCents)}</span> at risk in month
              #{run.seed}.
            </p>
            <p className="mt-2 text-sm text-agree">
              ✓ Caught {run.accuracy.detected} of {run.accuracy.seededBreaks} hidden mistakes, with{' '}
              {run.accuracy.falseAlarms} false alarms.
            </p>
          </div>

          <div className={selectedBreak ? 'grid gap-6 lg:grid-cols-5' : ''}>
            {/* Table */}
            <div className="min-w-0 lg:col-span-3">
              <div className="mb-3 flex items-center gap-2 text-sm text-muted">
                Type
                <select
                  className="rounded-md border border-rule bg-white px-3 py-2 text-ink"
                  value={type}
                  onChange={(e) => {
                    setType(e.target.value as BreakType | '')
                    setPage(0)
                    setSelected(null)
                  }}
                >
                  <option value="">All types ({run.breaks})</option>
                  {BREAK_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {BREAK_LABELS[t]} ({run.breaksByType[t] ?? 0})
                    </option>
                  ))}
                </select>
              </div>

              <div className="overflow-hidden rounded-md border border-rule bg-white">
                <table className="w-full table-fixed text-left text-sm">
                  <thead className="text-muted">
                    <tr className="border-b border-rule">
                      <th className="px-4 py-3 font-medium">What’s wrong</th>
                      <th className="w-36 px-4 py-3 text-right font-medium">Amount</th>
                      <th className="w-28 px-4 py-3 font-medium">AI</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pageItems.map((b) => (
                      <tr
                        key={b.index}
                        tabIndex={0}
                        onClick={() => {
                          setSelected(b.index)
                          setExplainError(null)
                        }}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault()
                            setSelected(b.index)
                            setExplainError(null)
                          }
                        }}
                        className={`cursor-pointer border-b border-rule last:border-0 focus-visible:outline-2 focus-visible:outline-link ${
                          b.index === selected ? 'bg-link/10' : 'hover:bg-paper'
                        }`}
                      >
                        <td className="px-4 py-3">{BREAK_LABELS[b.breakType]}</td>
                        <td className="px-4 py-3 text-right">{formatCents(b.amountImpactCents)}</td>
                        <td className="px-4 py-3 text-muted">{explanations[b.index] ? '✓ Explained' : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {totalPages > 1 && (
                <div className="mt-3 flex items-center justify-between text-sm text-muted">
                  <span>
                    Page {page + 1} of {totalPages}
                  </span>
                  <div className="flex gap-2">
                    <button
                      className="rounded-md border border-rule bg-white px-3 py-1.5 text-ink disabled:opacity-40"
                      disabled={page === 0}
                      onClick={() => setPage(page - 1)}
                    >
                      Previous
                    </button>
                    <button
                      className="rounded-md border border-rule bg-white px-3 py-1.5 text-ink disabled:opacity-40"
                      disabled={page + 1 >= totalPages}
                      onClick={() => setPage(page + 1)}
                    >
                      Next
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Detail panel */}
            {selectedBreak && (
              <aside className="min-w-0 space-y-5 rounded-md border border-rule bg-white p-5 text-sm lg:col-span-2">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <h3 className="font-serif text-2xl">{BREAK_LABELS[selectedBreak.breakType]}</h3>
                    <p className="text-muted">{formatCents(selectedBreak.amountImpactCents)} affected</p>
                  </div>
                  <button className="rounded-md px-2 py-1 text-muted hover:bg-paper" onClick={() => setSelected(null)}>
                    Close
                  </button>
                </div>

                {explanation ? (
                  <div className="space-y-3">
                    <p className="text-base leading-relaxed">{explanation.cause}</p>
                    {explanation.suggestedFix && (
                      <p className="leading-relaxed">
                        <span className="font-semibold">Suggested fix: </span>
                        {explanation.suggestedFix}
                      </p>
                    )}
                    <p className="text-xs text-muted">
                      {explanation.confidence ? `${CONFIDENCE_TEXT[explanation.confidence]}. ` : ''}
                      Written by AI as advice; it never changes data.
                    </p>
                  </div>
                ) : (
                  <div>
                    <button
                      onClick={() => explain(selectedBreak.index)}
                      disabled={explaining}
                      className="rounded-md bg-hero px-4 py-2 font-semibold text-hero-ink hover:brightness-125 disabled:opacity-50"
                    >
                      {explaining ? 'Asking the AI…' : 'Explain with AI'}
                    </button>
                    {aiLeft != null && <p className="mt-2 text-xs text-muted">{aiLeft} AI explanations left today.</p>}
                  </div>
                )}

                {explainError && <p className="text-risk">{explainError}</p>}

                <div>
                  <h4 className="mb-2 font-semibold">The records</h4>
                  <ul className="space-y-2">
                    {selectedBreak.records.map((line, i) => (
                      <li key={i} className="break-words rounded-md bg-paper p-3 text-xs">
                        {line}
                      </li>
                    ))}
                  </ul>
                  <p className="mt-2 text-xs text-muted">Raw records, as sent to the AI. Amounts are in cents.</p>
                </div>
              </aside>
            )}
          </div>
        </>
      )}
    </section>
  )
}