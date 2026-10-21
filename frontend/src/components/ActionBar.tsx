import { useState } from 'react'
import { api } from '../api'
import type { RunSummary } from '../types'

type Props = {
  summary: RunSummary | null
  onChanged: () => void
}

export function ActionBar({ summary, onChanged }: Props) {
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const busy = status !== null

  async function explain(runId: number) {
    setStatus('AI is explaining breaks (about 2 minutes)…')
    const result = await api.explain(runId)
    if (result.failedBatches > 0) {
      setError(`${result.failedBatches} AI batch(es) failed. Click "Explain remaining" to retry.`)
    }
    onChanged()
  }

  async function runDemo() {
    setError(null)
    try {
      setStatus('Generating sample data…')
      await api.generate()
      setStatus('Reconciling…')
      const run = await api.run()
      onChanged()
      await explain(run.runId)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setStatus(null)
    }
  }

  async function explainRemaining() {
    if (!summary) return
    setError(null)
    try {
      await explain(summary.runId)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setStatus(null)
    }
  }

  const unexplained = summary ? summary.breaks - summary.explained : 0

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex gap-2">
        {unexplained > 0 && (
          <button
            className="rounded-md border border-hero-muted/40 px-3 py-2 text-sm text-hero-ink hover:bg-white/10 disabled:opacity-50"
            disabled={busy}
            onClick={explainRemaining}
          >
            Explain remaining ({unexplained})
          </button>
        )}
        <button
          className="rounded-md bg-mint px-4 py-2 text-sm font-semibold text-hero hover:brightness-95 disabled:opacity-50"
          disabled={busy}
          onClick={runDemo}
        >
          Run demo
        </button>
      </div>
      {status && <p className="text-sm text-mint">{status}</p>}
      {error && <p className="text-sm text-coral">{error}</p>}
    </div>
  )
}