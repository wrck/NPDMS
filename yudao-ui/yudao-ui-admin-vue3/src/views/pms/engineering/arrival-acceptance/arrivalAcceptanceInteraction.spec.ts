import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  arrivalAcceptanceLayout,
  arrivalResolutionOptions,
  createArrivalIntentStore,
  createArrivalWriteBarrier,
  evidenceSyncPresentation,
  formatWireDateTime,
  pickerValueToWireDateTime,
  projectArrivalProgress,
  resolveArrivalCommandFailure,
  runArrivalGuardedWrite,
  runArrivalIntent,
  shouldExposeArrivalAction,
  successorReasonPresentation,
  truncateEvidenceName,
  wireDateTimeToPickerValue
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
        arrivedAt: 1788055200000,
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

  it('uses both permission and server allowedActions without deriving lifecycle locally', () => {
    const actions = ['EDIT_DRAFT', 'SUBMIT', 'RESOLVE_DIFFERENCE']
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
    expect(
      resolveArrivalCommandFailure({
        response: { status: 409, data: { data: { recoveryAction: 'RETRY_SAME_KEY' } } }
      })
    ).toBe('RETAIN_INTENT')
    expect(
      resolveArrivalCommandFailure({
        response: { status: 409, data: { data: { recoveryAction: 'REFRESH_OWNER_FACTS' } } }
      })
    ).toBe('REFRESH_OWNER_FACTS')
    expect(
      resolveArrivalCommandFailure({
        response: { status: 409, data: { data: { recoveryAction: 'START_NEW_INTENT' } } }
      })
    ).toBe('START_NEW_INTENT')
  })

  it('uses epoch milliseconds for picker, request and response presentation', () => {
    expect(pickerValueToWireDateTime('1788055200000')).toBe(1788055200000)
    expect(wireDateTimeToPickerValue('1788055200000')).toBe(1788055200000)
    expect(formatWireDateTime(1788055200000)).toMatch(/^2026-08-/)
    expect(() => pickerValueToWireDateTime('2026-08-30T10:00:00')).toThrow()
  })

  it('retains one key for unknown/in-progress outcomes and refreshes stale facts', async () => {
    const store = createArrivalIntentStore(() => 'key-1')
    const refreshAfterFailure = vi.fn().mockResolvedValue(undefined)
    const inProgress = await runArrivalIntent({
      intent: 'submit:10:3',
      store,
      call: vi.fn().mockRejectedValue({
        response: { status: 409, data: { data: { recoveryAction: 'RETRY_SAME_KEY' } } }
      }),
      refreshAfterSuccess: vi.fn(),
      refreshAfterFailure
    })
    expect(inProgress).toMatchObject({ commandSucceeded: false, keyRetained: true })
    expect(store.key('submit:10:3')).toBe('key-1')
    expect(refreshAfterFailure).not.toHaveBeenCalled()

    const stale = await runArrivalIntent({
      intent: 'confirm:10:4',
      store,
      call: vi.fn().mockRejectedValue({
        response: { status: 409, data: { data: { recoveryAction: 'REFRESH_AGGREGATE' } } }
      }),
      refreshAfterSuccess: vi.fn(),
      refreshAfterFailure
    })
    expect(stale).toMatchObject({ commandSucceeded: false, refreshSucceeded: true })
    expect(refreshAfterFailure).toHaveBeenCalledOnce()
  })

  it('never reissues a successful command when only the refresh fails', async () => {
    const factory = vi.fn().mockReturnValueOnce('key-1').mockReturnValueOnce('key-2')
    const store = createArrivalIntentStore(factory)
    const call = vi.fn().mockResolvedValue({ id: 10 })
    const outcome = await runArrivalIntent({
      intent: 'submit:10:3',
      store,
      call,
      refreshAfterSuccess: vi.fn().mockRejectedValue(new Error('refresh failed')),
      refreshAfterFailure: vi.fn()
    })
    expect(outcome).toEqual({
      commandSucceeded: true,
      recovery: null,
      keyRetained: false,
      refreshSucceeded: false,
      retryRefresh: expect.any(Function)
    })
    expect(call).toHaveBeenCalledOnce()
    await outcome.retryRefresh?.().catch(() => undefined)
    expect(call).toHaveBeenCalledOnce()
    expect(store.key('submit:10:3')).toBe('key-2')
  })

  it('blocks PATCH behind the shared refresh barrier and then uses the refreshed version', async () => {
    const barrier = createArrivalWriteBarrier()
    const patch = vi.fn()
    let aggregateVersion = 4
    barrier.register(async () => {
      aggregateVersion = 7
    })

    const first = await barrier.beforeWrite()
    if (first === 'PROCEED') await patch(aggregateVersion)
    expect(first).toBe('REFRESHED')
    expect(patch).not.toHaveBeenCalled()

    const second = await barrier.beforeWrite()
    if (second === 'PROCEED') await patch(aggregateVersion)
    expect(patch).toHaveBeenCalledExactlyOnceWith(7)
  })

  it('refreshes owner facts after PATCH scope stale and retries with the latest version', async () => {
    const barrier = createArrivalWriteBarrier()
    const patch = vi
      .fn()
      .mockRejectedValueOnce({
        response: { status: 409, data: { data: { recoveryAction: 'REFRESH_OWNER_FACTS' } } }
      })
      .mockResolvedValueOnce({ version: 8 })
    let aggregateVersion = 4
    const first = await runArrivalGuardedWrite({
      barrier,
      call: () => patch(aggregateVersion),
      refreshAfterSuccess: vi.fn().mockResolvedValue(undefined),
      refreshAfterConflict: async () => {
        aggregateVersion = 7
      }
    })
    expect(first).toMatchObject({
      writeCalled: true,
      succeeded: false,
      recovery: 'REFRESH_OWNER_FACTS',
      refreshSucceeded: true
    })

    const second = await runArrivalGuardedWrite({
      barrier,
      call: () => patch(aggregateVersion),
      refreshAfterSuccess: vi.fn().mockResolvedValue(undefined),
      refreshAfterConflict: vi.fn()
    })
    expect(second).toMatchObject({ writeCalled: true, succeeded: true })
    expect(patch.mock.calls).toEqual([[4], [7]])
  })

  it('blocks the next write when PATCH succeeds but its refresh fails', async () => {
    const barrier = createArrivalWriteBarrier()
    const patch = vi.fn().mockResolvedValue({ version: 5 })
    let aggregateVersion = 4
    const refresh = vi
      .fn()
      .mockRejectedValueOnce(new Error('refresh failed'))
      .mockImplementationOnce(async () => {
        aggregateVersion = 7
      })
      .mockResolvedValue(undefined)

    const first = await runArrivalGuardedWrite({
      barrier,
      call: () => patch(aggregateVersion),
      refreshAfterSuccess: refresh,
      refreshAfterConflict: vi.fn()
    })
    expect(first).toMatchObject({
      writeCalled: true,
      succeeded: true,
      refreshSucceeded: false
    })
    expect(barrier.hasPending()).toBe(true)

    const second = await runArrivalGuardedWrite({
      barrier,
      call: () => patch(aggregateVersion),
      refreshAfterSuccess: refresh,
      refreshAfterConflict: vi.fn()
    })
    expect(second).toEqual({ writeCalled: false, barrierResult: 'REFRESHED' })
    expect(patch).toHaveBeenCalledTimes(1)

    const third = await runArrivalGuardedWrite({
      barrier,
      call: () => patch(aggregateVersion),
      refreshAfterSuccess: refresh,
      refreshAfterConflict: vi.fn()
    })
    expect(third).toMatchObject({ writeCalled: true, succeeded: true })
    expect(patch.mock.calls).toEqual([[4], [7]])
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
