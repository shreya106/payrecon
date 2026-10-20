export type BreakType =
  | 'MISSING_IN_LEDGER'
  | 'MISSING_IN_PROCESSOR'
  | 'DUPLICATE'
  | 'AMOUNT_MISMATCH'
  | 'IN_TRANSIT'
  | 'MISSING_IN_BANK'
  | 'UNEXPLAINED'

export type Confidence = 'LOW' | 'MEDIUM' | 'HIGH'

export type Accuracy = {
  seededBreaks: number
  detected: number
  missed: number
  falseAlarms: number
}

export type RunSummary = {
  runId: number
  status: string
  startedAt: string
  completedAt: string | null
  totalGroups: number
  matched: number
  breaks: number
  explained: number
  amountAtRiskCents: number
  breaksByType: Partial<Record<BreakType, number>>
  accuracy: Accuracy | null
}

export type BreakRow = {
  groupId: number
  breakType: BreakType
  amountImpactCents: number
  aiConfidence: Confidence | null
  aiCause: string | null
}

export type Page<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type RecordView = {
  source: 'PROCESSOR' | 'LEDGER' | 'BANK'
  recordId: number
  externalId: string
  type: string | null
  description: string | null
  grossAmountCents: number | null
  feeAmountCents: number | null
  netAmountCents: number | null
  amountCents: number | null
  processorRef: string | null
  payoutExternalId: string | null
  date: string | null
}

export type AiExplanation = {
  cause: string
  confidence: Confidence | null
  suggestedFix: string | null
  explainedAt: string | null
}

export type BreakDetail = {
  groupId: number
  runId: number
  outcome: string
  breakType: BreakType | null
  matchMethod: string | null
  amountImpactCents: number
  explanation: AiExplanation | null
  records: RecordView[]
}