import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import { ActionBar } from './components/ActionBar'
import { BreakDetailPanel } from './components/BreakDetailPanel'
import { BreaksTable } from './components/BreaksTable'
import { SummaryCards } from './components/SummaryCards'
import type { RunSummary } from './types'

export default function App() {
  const [summary, setSummary] = useState<RunSummary | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const loadSummary = useCallback(() => {
    api
      .latestRun()
      .then((s) => {
        setSummary(s)
        setLoaded(true)
        setError(null)
      })
      .catch((err: Error) => setError(err.message))
  }, [])

  useEffect(() => {
    loadSummary()
  }, [loadSummary, refreshKey])

  function refresh() {
    setSelectedId(null)
    setRefreshKey((k) => k + 1)
  }

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-start justify-between gap-4 p-4">
          <div>
            <h1 className="text-xl font-bold">PayRecon</h1>
            <p className="text-sm text-gray-500">
              Reconciles Stripe, ledger and bank records, and explains every break.
            </p>
          </div>
          {import.meta.env.DEV ? (
            <ActionBar summary={summary} onChanged={refresh} />
          ) : (
            <p className="max-w-xs text-right text-xs text-gray-500">
              Read-only demo on synthetic data. Free hosting: the first load may take a minute.
            </p>
          )}
        </div>
      </header>

      <main className="mx-auto max-w-7xl space-y-4 p-4">
        {error && <p className="text-red-600">Error: {error}</p>}
        {!loaded && !error && <p className="text-gray-500">Loading…</p>}
        {loaded && !summary && (
          <p className="text-gray-600">No reconciliation runs yet. Click <strong>Run demo</strong> to start.</p>
        )}

        {summary && (
          <>
            <SummaryCards summary={summary} />
            <div className="grid gap-4 lg:grid-cols-5">
              <div className="min-w-0 lg:col-span-3">
                <BreaksTable
                  key={`${summary.runId}-${refreshKey}`}
                  runId={summary.runId}
                  selectedId={selectedId}
                  onSelect={setSelectedId}
                />
              </div>
              <div className="min-w-0 lg:col-span-2">
                <BreakDetailPanel key={`detail-${refreshKey}`} groupId={selectedId} />
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  )
}