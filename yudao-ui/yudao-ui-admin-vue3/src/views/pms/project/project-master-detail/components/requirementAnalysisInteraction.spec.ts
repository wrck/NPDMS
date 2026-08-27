import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import {
  buildRequirementAttachment,
  buildSectionPatch,
  containsEmbeddedMedia,
  createRequirementIntentStore,
  patchRequirementSectionAndReload,
  requirementAnalysisLayout,
  requirementIntentOf,
  resolvePendingAttachmentSync
} from './requirementAnalysisInteraction'

const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: request }))

describe('F-SOL-003 requirement analysis runtime contracts', () => {
  beforeEach(() => {
    request.get.mockReset()
    request.post.mockReset()
    request.put.mockReset()
  })

  it('sends only genuinely changed section fields with If-Match', () => {
    const patch = buildSectionPatch(7, 8, 9, '<p>新目标</p>', '<p>旧目标</p>', [], [])
    expect(patch).toEqual({
      submittedFields: ['value'],
      value: '<p>新目标</p>',
      expectedPreparationVersion: 7,
      expectedContentVersion: 8,
      expectedProjectVersion: 9
    })

    RequirementAnalysisApi.patchSection(11, 12, patch)
    expect(request.put).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'PATCH',
        url: '/api/v1/pms/preparations/11/items/12',
        params: { type: 'PRE_04' },
        data: patch,
        headers: { 'If-Match': '7' }
      })
    )
  })

  it('reloads authoritative overview and detail only after PATCH succeeds', async () => {
    const order: string[] = []
    await patchRequirementSectionAndReload(
      async () => {
        order.push('patch')
        return { operationId: 'op-1' }
      },
      async () => {
        order.push('reload')
      }
    )
    expect(order).toEqual(['patch', 'reload'])

    const reload = vi.fn()
    await expect(
      patchRequirementSectionAndReload(async () => Promise.reject(new Error('unknown')), reload)
    ).rejects.toThrow('unknown')
    expect(reload).not.toHaveBeenCalled()
  })

  it('freezes full file facts and preserves the server attachment slot', () => {
    const attachment = buildRequirementAttachment(
      { artifactId: 21, versionNo: 3, referenceId: 31, referenceKey: 'upload-slot' },
      {
        artifactId: 21,
        name: 'network-topology.pdf',
        categoryCode: 'REQUIREMENT_ANALYSIS_ATTACHMENT',
        ownerContext: 'SOL',
        lifecycleStatus: 'ACTIVE',
        artifactVersion: 7,
        reference: {
          referenceId: 31,
          artifactId: 21,
          versionNo: 3,
          ownerContext: 'SOL',
          objectType: 'REQUIREMENT_ANALYSIS_SECTION',
          objectId: '12',
          purposeCode: 'SECTION_ATTACHMENT',
          referenceKey: 'frozen-slot',
          sensitivityCode: 'INTERNAL',
          status: 'ACTIVE',
          scopeVersion: 8,
          referenceVersion: 9,
          createdAt: '',
          updatedAt: ''
        },
        allowedActions: [],
        createdAt: ''
      },
      {
        id: 41,
        versionNo: 3,
        sha256: 'sha',
        sizeBytes: 1,
        mediaType: 'application/pdf',
        scanStatus: 'SKIPPED',
        availabilityStatus: 'AVAILABLE',
        availabilityVersion: 10,
        createdBy: 1,
        createdAt: ''
      },
      'frozen-slot'
    )
    expect(attachment).toMatchObject({
      artifactId: 21,
      versionNo: 3,
      referenceKey: 'frozen-slot',
      fileFactVersion: {
        artifactVersion: 7,
        referenceVersion: 9,
        availabilityVersion: 10
      },
      scopeVersion: 8
    })
  })

  it('rejects pasted media nodes while allowing ordinary rich text', () => {
    expect(containsEmbeddedMedia('<p>说明</p><img src="https://files.example/a.png">')).toBe(true)
    expect(containsEmbeddedMedia('<video><source src="file.mp4"></video>')).toBe(true)
    expect(containsEmbeddedMedia('<p>普通正文</p><a href="https://example.com">参考</a>')).toBe(
      false
    )
  })

  it('restores an unsynchronized attachment intent after an unknown PATCH response', () => {
    const server = [{ referenceKey: 'old', versionNo: 1 }]
    const pending = [{ referenceKey: 'old', versionNo: 2 }]
    expect(resolvePendingAttachmentSync(server, pending)).toEqual({
      attachments: pending,
      syncPending: true
    })
    expect(resolvePendingAttachmentSync(pending, pending)).toEqual({
      attachments: pending,
      syncPending: false
    })
  })

  it('keeps one idempotency key for an unknown response and rotates on a new intent', () => {
    let sequence = 0
    const keys = createRequirementIntentStore(() => `key-${++sequence}`)
    const same = requirementIntentOf('COMPLETE', { preparationId: 1, version: 2 })
    const changed = requirementIntentOf('COMPLETE', { preparationId: 1, version: 3 })

    expect(keys.key(same)).toBe('key-1')
    expect(keys.key(same)).toBe('key-1')
    expect(keys.key(changed)).toBe('key-2')
    keys.complete(same)
    expect(keys.key(same)).toBe('key-3')
  })

  it('keeps create, complete and revise commands on distinct stable intents', () => {
    RequirementAnalysisApi.createInitialDraft(1, 'create-key')
    RequirementAnalysisApi.completeDraft(3, 4, 5, 2, 'complete-key')
    RequirementAnalysisApi.createNextDraft(3, 4, 5, 2, 'revise-key')

    expect(
      request.post.mock.calls.map(([argument]) => argument.headers['Idempotency-Key'])
    ).toEqual(['create-key', 'complete-key', 'revise-key'])
    expect(request.post.mock.calls[1][0].headers['If-Match']).toBe('4')
    expect(request.post.mock.calls[2][0].headers['If-Match']).toBe('4')
  })

  it.each([
    [320, 'mobile'],
    [768, 'tablet'],
    [1024, 'desktop'],
    [1440, 'desktop']
  ])('selects the responsive workspace branch at %ipx', (width, expected) => {
    expect(requirementAnalysisLayout(width)).toBe(expected)
  })
})
