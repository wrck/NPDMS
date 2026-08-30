import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import {
  arrivalAcceptanceLayout,
  arrivalResolutionOptions,
  createArrivalIntentStore,
  evidenceSyncPresentation,
  projectArrivalProgress,
  resolveArrivalCommandFailure,
  shouldExposeArrivalAction,
  successorReasonPresentation,
  truncateEvidenceName
} from './arrivalAcceptanceInteraction'
import * as ArrivalApi from '@/api/pms/engineering/arrival-acceptance'

const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: request }))

describe('F-IMP-002 arrival acceptance interactions', () => {
  beforeEach(() => {
    request.get.mockReset()
    request.post.mockReset()
    request.put.mockReset()
  })

  it('keeps partial arrival distinct from project completion', () => {
    expect(projectArrivalProgress('PARTIALLY_ACCEPTED')).toEqual({
      label: '部分到货',
      milestoneComplete: false,
      tone: 'warning'
    })
    expect(projectArrivalProgress('ACCEPTED').milestoneComplete).toBe(false)
    expect(projectArrivalProgress('CONFIRMED').milestoneComplete).toBe(false)
  })

  it('keeps differences, supplement and exemption invalidation visible as server facts', () => {
    expect(projectArrivalProgress('DIFFERENCE_PENDING').label).toBe('差异待处理')
    expect(evidenceSyncPresentation('ARCHIVE_ACK_PENDING_RETRY')).toMatchObject({
      label: '已签收，归档回执待重试',
      arrivalConfirmed: true
    })
    expect(evidenceSyncPresentation('PUBLISHED_PENDING_ACC').arrivalConfirmed).toBe(true)
    expect(evidenceSyncPresentation(null).label).toBe('尚未上传签收证据')
    expect(successorReasonPresentation('SUPPLEMENT')).toBe('补签后继')
    expect(successorReasonPresentation('EXEMPTION_INVALIDATION')).toBe('豁免失效后继')
    expect(arrivalResolutionOptions('DIFFERENCE_PENDING', 'OPEN')).toEqual([
      'SUPPLEMENT',
      'KEEP_REJECTED',
      'EXEMPT',
      'CLOSE'
    ])
    expect(arrivalResolutionOptions('CONFIRMED', 'REJECTED')).toEqual([
      'SUPPLEMENT',
      'EXEMPT',
      'CLOSE'
    ])
    expect(arrivalResolutionOptions('CONFIRMED', 'EXEMPTED')).toEqual([])
  })

  it('maps the eight locked routes and command headers without legacy fallbacks', () => {
    ArrivalApi.getArrivalPage({ pageNo: 1, pageSize: 20 })
    ArrivalApi.getArrivalDetail('9007199254740993')
    ArrivalApi.createArrival(
      {
        projectId: '9007199254740993',
        batchCode: 'ARR-01',
        logisticsNo: 'L-01',
        arrivedAt: '2026-08-30T10:00:00',
        signerName: '张三',
        expectedDeliveryScopeVersion: 3
      },
      'create-key'
    )
    ArrivalApi.patchArrival(8, 4, { signerName: '李四' })
    ArrivalApi.submitArrival(8, 5, 'submit-key')
    ArrivalApi.confirmArrival(8, 6, 'confirm-key')
    const evidenceRevision = {
      artifactId: 1,
      referenceKey: 'receipt',
      versionNo: 1,
      scopeVersion: 1,
      fileFactVersion: { artifactVersion: 1, referenceVersion: 1, availabilityVersion: 1 },
      hash: 'sha'
    }
    ArrivalApi.raiseArrivalDifference(
      8,
      7,
      {
        arrivalLineId: 9,
        expectedLineVersion: 2,
        differenceTypeCode: 'QUANTITY_MISMATCH',
        scopeSnapshot: {
          scopeType: 'ORDER_MODEL_QUANTITY',
          orderLineId: 10,
          productCode: 'P',
          modelCode: null,
          quantity: 1,
          unitCode: '台'
        },
        reason: '短少',
        riskDescription: null,
        evidenceRevision
      },
      'raise-key'
    )
    ArrivalApi.resolveArrivalDifference(
      8,
      8,
      {
        resolutionType: 'CLOSE',
        differenceId: 11,
        expectedDifferenceRevision: 1,
        expectedDifferenceVersion: 2,
        reason: '已核对关闭',
        evidenceRevision
      },
      'resolve-key'
    )

    expect(request.get.mock.calls.map(([config]) => config.url)).toEqual([
      '/api/v1/pms/arrival-acceptances',
      '/api/v1/pms/arrival-acceptances/9007199254740993'
    ])
    expect(request.put).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'PATCH',
        headers: { 'If-Match': '4' }
      })
    )
    expect(request.post.mock.calls.map(([config]) => config.headers)).toEqual([
      { 'Idempotency-Key': 'create-key' },
      { 'If-Match': '5', 'Idempotency-Key': 'submit-key' },
      { 'If-Match': '6', 'Idempotency-Key': 'confirm-key' },
      { 'If-Match': '7', 'Idempotency-Key': 'raise-key' },
      { 'If-Match': '8', 'Idempotency-Key': 'resolve-key' }
    ])
  })

  it('keeps the new workspace on stable references, allowedActions and responsive components', () => {
    const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
    const page = read('./index.vue')
    const evidence = read('./components/ArrivalEvidencePanel.vue')
    const lines = read('./components/ArrivalLineEditor.vue')
    const differences = read('./components/ArrivalDifferencePanel.vue')

    expect(page).toContain("allowedActions.includes('EDIT_DRAFT')")
    expect(page).toContain("allowedActions.includes('CONFIRM')")
    expect(page).toContain('v-hasPermi="[\'pms:arrival-acceptance:confirm\']"')
    expect(evidence).toContain('<PmsFileUploader')
    expect(evidence).toContain('referenceKey')
    expect(evidence).not.toMatch(/attachmentUrl|原始文件地址.*el-input/)
    expect(differences).toContain('arrivalResolutionOptions')
    for (const source of [page, evidence, lines, differences]) {
      expect(source).not.toContain('/pms/eng-arrival')
      expect(source).toContain('@media (width <= 767px)')
    }
  })

  it('uses both permission and server allowedActions without deriving lifecycle locally', () => {
    const actions = ['PATCH_DRAFT', 'SUBMIT', 'RESOLVE_DIFFERENCE']
    expect(shouldExposeArrivalAction('SUBMIT', actions, true)).toBe(true)
    expect(shouldExposeArrivalAction('CONFIRM', actions, true)).toBe(false)
    expect(shouldExposeArrivalAction('SUBMIT', actions, false)).toBe(false)
  })

  it('refreshes the whole aggregate on stale conflicts and preserves unknown intents', () => {
    expect(
      resolveArrivalCommandFailure({
        response: { status: 409, data: { data: { recoveryAction: 'REFRESH_AGGREGATE' } } }
      })
    ).toBe('REFRESH_AGGREGATE')
    expect(resolveArrivalCommandFailure(new Error('network response unknown'))).toBe(
      'RETAIN_INTENT'
    )
    expect(resolveArrivalCommandFailure({ response: { status: 422 } })).toBe('SURFACE_ERROR')
  })

  it('retains one idempotency key for an unknown response and rotates after success', () => {
    const factory = vi.fn().mockReturnValueOnce('key-1').mockReturnValueOnce('key-2')
    const store = createArrivalIntentStore(factory)
    expect(store.key('submit:10:3')).toBe('key-1')
    expect(store.key('submit:10:3')).toBe('key-1')
    store.complete('submit:10:3')
    expect(store.key('submit:10:3')).toBe('key-2')
  })

  it('keeps long evidence names readable without changing the stable file identity', () => {
    const fileName = `${'超长签收文件名'.repeat(12)}.pdf`
    const shown = truncateEvidenceName(fileName, 36)
    expect(shown).toHaveLength(36)
    expect(shown.endsWith('…')).toBe(true)
    expect(fileName.endsWith('.pdf')).toBe(true)
  })

  it.each([
    [320, 'mobile'],
    [768, 'tablet'],
    [1024, 'desktop'],
    [1440, 'wide']
  ])('selects the responsive workspace branch at %ipx', (width, expected) => {
    expect(arrivalAcceptanceLayout(width)).toBe(expected)
  })
})
