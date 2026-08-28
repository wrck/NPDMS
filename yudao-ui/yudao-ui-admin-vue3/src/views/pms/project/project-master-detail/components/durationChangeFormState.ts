import type { ConstructionPlanChangeVO } from '@/api/pms/engineering/construction-plan'
import type { DurationCalculationBasis } from '@/api/pms/engineering/construction-plan'

export interface DurationChangeFormState {
  calculationBasis: DurationCalculationBasis
  startDate: string
  endDate?: string
  durationDays?: number
  reasonType: string
  reasonDetail: string
  customerEvidenceFileId?: number
  customerEvidenceFileVersion?: number
  customerEvidenceReferenceKey?: string
}

export const formStateFromChange = (change: ConstructionPlanChangeVO): DurationChangeFormState => ({
  ...change.candidateRevision,
  reasonType: change.reasonType,
  reasonDetail: change.reasonDetail || '',
  customerEvidenceFileId: change.customerEvidenceFileId,
  customerEvidenceFileVersion: change.customerEvidenceFileVersion,
  customerEvidenceReferenceKey: change.customerEvidenceReferenceKey
})

export const reconcilePatchResponseLoss = (
  local: DurationChangeFormState,
  current: ConstructionPlanChangeVO
) => ({
  current,
  form: structuredClone(local),
  baseline: formStateFromChange(current)
})
