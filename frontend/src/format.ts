import type { BreakType, Confidence } from './types'

export function formatCents(cents: number | null | undefined): string {
  if (cents == null) return '—'
  return (cents / 100).toLocaleString('en-US', { style: 'currency', currency: 'USD' })
}

export const BREAK_LABELS: Record<BreakType, string> = {
  MISSING_IN_LEDGER: 'Missing in ledger',
  MISSING_IN_PROCESSOR: 'Missing in Stripe',
  DUPLICATE: 'Duplicate',
  AMOUNT_MISMATCH: 'Amount mismatch',
  IN_TRANSIT: 'In transit',
  MISSING_IN_BANK: 'Missing in bank',
  UNEXPLAINED: 'Unexplained deposit',
}

export const BREAK_TYPES = Object.keys(BREAK_LABELS) as BreakType[]

export const CONFIDENCE_STYLES: Record<Confidence, string> = {
  HIGH: 'bg-green-100 text-green-800',
  MEDIUM: 'bg-yellow-100 text-yellow-800',
  LOW: 'bg-gray-200 text-gray-700',
}