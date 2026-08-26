import { describe, expect, it } from 'vitest'
import { resolveFileUploadMode, useFileSlotState } from './useFileSlotState'
import {
  formStateFromChange,
  reconcilePatchResponseLoss
} from '../../views/pms/project/project-master-detail/components/durationChangeFormState'
import type { ConstructionPlanChangeVO } from '@/api/pms/engineering/construction-plan'

const change = (overrides: Partial<ConstructionPlanChangeVO> = {}): ConstructionPlanChangeVO => ({
  changeId: 41,
  baseRevisionId: 10,
  candidateRevisionId: 11,
  candidateRevision: {
    revisionId: 11,
    revisionNo: 2,
    calculationBasis: 'DATE_RANGE',
    startDate: '2026-08-01',
    endDate: '2026-08-31',
    durationDays: 31,
    createdBy: 1,
    createdAt: '2026-08-27T00:00:00',
    version: 1,
    current: false
  },
  status: 'DRAFT',
  reasonType: 'CUSTOMER_DELAY',
  reasonDetail: '原始说明',
  customerEvidenceRequired: true,
  applicantUserId: 1,
  createdAt: '2026-08-27T00:00:00',
  version: 3,
  ...overrides
})

describe('F-PLT-001 file interaction state', () => {
  it('keeps the stable slot after detach and rebinds it through ADD_VERSION', () => {
    const slot = useFileSlotState()
    slot.detached({
      artifactId: 901,
      versionNo: 2,
      referenceId: 801,
      referenceKey: 'customer-delay',
      factVersion: 7,
      status: 'DETACHED'
    })

    expect(slot.state).toMatchObject({
      artifactId: 901,
      referenceKey: 'customer-delay',
      referenceVersion: 7
    })
    expect(resolveFileUploadMode(slot.state.artifactId)).toBe('ADD_VERSION')
  })

  it('uses the refreshed change version after a committed PATCH response is lost', () => {
    const before = change()
    const local = formStateFromChange(before)
    local.reasonDetail = '尚未落库的补充说明'
    local.customerEvidenceFileId = 901
    local.customerEvidenceFileVersion = 2
    local.customerEvidenceReferenceKey = 'customer-delay'
    const persisted = change({
      version: 4,
      customerEvidenceFileId: 901,
      customerEvidenceFileVersion: 2,
      customerEvidenceReferenceKey: 'customer-delay'
    })

    const recovered = reconcilePatchResponseLoss(local, persisted)

    expect(recovered.current.version).toBe(4)
    expect(recovered.baseline.customerEvidenceFileId).toBe(901)
    expect(recovered.form.reasonDetail).toBe('尚未落库的补充说明')
    expect(recovered.form.customerEvidenceFileId).toBe(recovered.baseline.customerEvidenceFileId)
  })
})
