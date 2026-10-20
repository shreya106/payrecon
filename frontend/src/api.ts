import type { BreakDetail, BreakRow, BreakType, Page, RunSummary } from './types'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, options)
  if (!res.ok) {
    throw new Error(`${options?.method ?? 'GET'} ${url} failed (HTTP ${res.status})`)
  }
  return res.json() as Promise<T>
}

export const api = {
  /** Returns null when no run exists yet (the backend answers 404). */
  async latestRun(): Promise<RunSummary | null> {
    const res = await fetch('/api/recon/runs/latest')
    if (res.status === 404) return null
    if (!res.ok) throw new Error(`GET /api/recon/runs/latest failed (HTTP ${res.status})`)
    return res.json() as Promise<RunSummary>
  },

  breaks(
    runId: number,
    params: { type?: BreakType; includeInTransit: boolean; page: number; size: number },
  ) {
    const query = new URLSearchParams({
      page: String(params.page),
      size: String(params.size),
      includeInTransit: String(params.includeInTransit),
    })
    if (params.type) query.set('type', params.type)
    return request<Page<BreakRow>>(`/api/recon/runs/${runId}/breaks?${query}`)
  },

  breakDetail(groupId: number) {
    return request<BreakDetail>(`/api/recon/breaks/${groupId}`)
  },

  generate() {
    return request<unknown>('/api/demo/generate', { method: 'POST' })
  },

  run() {
    return request<{ runId: number }>('/api/recon/run', { method: 'POST' })
  },

  explain(runId: number) {
    return request<{ explained: number; failedBatches: number }>(
      `/api/recon/${runId}/explain`,
      { method: 'POST' },
    )
  },
}