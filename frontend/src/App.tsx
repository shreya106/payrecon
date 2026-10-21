import { useCallback, useEffect, useState } from 'react'
import type { CSSProperties } from 'react'
import { api } from './api'
import { ActionBar } from './components/ActionBar'
import { BreakDetailPanel } from './components/BreakDetailPanel'
import { BreaksTable } from './components/BreaksTable'
import { Checks, Hero, MoneyFlow } from './components/Overview'
import type { RunSummary } from './types'

const GITHUB_URL = 'https://github.com/YOUR-USERNAME/payrecon'

function Logo() {
  return (
    <span className="flex items-center gap-2.5">
      <span className="flex items-center" aria-hidden="true">
        <span className="logo-dot h-3.5 w-3.5 rounded-full bg-stripe" style={{ '--from': '-14px' } as CSSProperties} />
        <span
          className="logo-dot -ml-1.5 h-3.5 w-3.5 rounded-full bg-ledger ring-2 ring-hero"
          style={{ animationDelay: '0.1s' }}
        />
        <span
          className="logo-dot -ml-1.5 h-3.5 w-3.5 rounded-full bg-bank ring-2 ring-hero"
          style={{ '--from': '14px', animationDelay: '0.2s' } as CSSProperties}
        />
      </span>
      <span className="text-lg font-semibold tracking-tight">PayRecon</span>
    </span>
  )
}

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
    <div className="min-h-screen">
      {/* Teal band: header + headline */}
      <div className="bg-hero text-hero-ink">
        <header className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-5">
          <div className="flex items-center gap-5">
            <Logo />
            <a href={GITHUB_URL} target="_blank" rel="noreferrer" className="text-sm text-mint hover:underline">
              GitHub
            </a>
          </div>
          {import.meta.env.DEV ? (
            <ActionBar summary={summary} onChanged={refresh} />
          ) : (
            <p className="text-right text-xs text-hero-muted">Demo on synthetic data. The first load can take a minute.</p>
          )}
        </header>
        <Hero summary={summary} />
      </div>

      <main className="mx-auto max-w-6xl px-6 pb-16">
        {error && <p className="py-10 text-risk">Couldn’t load results: {error}</p>}
        {!loaded && !error && <p className="py-10 text-muted">Loading…</p>}
        {loaded && !summary && (
          <p className="py-10 text-muted">No results yet. Run the demo to reconcile a sample month of payments.</p>
        )}

        <MoneyFlow />

        {summary && (
          <>
            <Checks summary={summary} />
            <div className={selectedId == null ? '' : 'grid gap-6 lg:grid-cols-5'}>
              <div className="min-w-0 lg:col-span-3">
                <BreaksTable
                  key={`${summary.runId}-${refreshKey}`}
                  runId={summary.runId}
                  counts={summary.breaksByType}
                  selectedId={selectedId}
                  onSelect={setSelectedId}
                />
              </div>
              {selectedId != null && (
                <div className="min-w-0 lg:col-span-2 lg:pt-14">
                  <BreakDetailPanel groupId={selectedId} onClose={() => setSelectedId(null)} />
                </div>
              )}
            </div>
          </>
        )}
      </main>

      <footer className="border-t border-rule">
        <p className="mx-auto max-w-6xl px-6 py-6 text-xs text-muted">
          Built with Java, Spring Boot, PostgreSQL, React, TypeScript and Spring AI. All data is synthetic.
        </p>
      </footer>
    </div>
  )
}