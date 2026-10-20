import { useEffect, useState } from 'react'

type RunSummary = {
  runId: number
  matched: number
  breaks: number
  amountAtRiskCents: number
}

function formatCents(cents: number): string {
  return (cents / 100).toLocaleString('en-US', { style: 'currency', currency: 'USD' })
}

export default function App() {
  const [summary, setSummary] = useState<RunSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/recon/runs/latest')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then(setSummary)
      .catch((err: Error) => setError(err.message))
  }, [])

  if (error) return <p className="p-8 text-red-600">Error: {error}</p>
  if (!summary) return <p className="p-8 text-gray-500">Loading…</p>

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold">PayRecon</h1>
      <p className="mt-2">Run #{summary.runId}</p>
      <p>{summary.matched} matched · {summary.breaks} breaks</p>
      <p className="font-semibold text-red-700">{formatCents(summary.amountAtRiskCents)} at risk</p>
    </div>
  )
}