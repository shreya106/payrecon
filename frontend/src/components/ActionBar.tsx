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
      onChanged() // show results right away, before the AI finishes
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
            className="rounded border border-gray-300 bg-white px-3 py-2 text-sm disabled:opacity-50"
            disabled={busy}
            onClick={explainRemaining}
          >
            Explain remaining ({unexplained})
          </button>
        )}
        <button
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          disabled={busy}
          onClick={runDemo}
        >
          Run demo
        </button>
      </div>
      {status && <p className="text-sm text-blue-700">{status}</p>}
      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  )
}