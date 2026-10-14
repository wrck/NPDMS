import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildEvidenceReference,
  createIntentKeyStore,
  intentOf,
  setChanged
} from './preparationInteraction'
import type { PatchPreparationItemReqVO } from '@/api/pms/engineering/preparation'
import * as PreparationApi from '@/api/pms/engineering/preparation'

const request = vi.hoisted(() => ({ put: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: request }))

describe('F-SOL-002 preparation runtime requests', () => {
  beforeEach(() => request.put.mockReset())

  it.each([
    { artifactId: 11, referenceId: 21, scopeVersion: 9 },
    { artifactId: '9007199254740993', referenceId: '9007199254740995', scopeVersion: '9007199254740997' }
  ])('freezes all three file versions and preserves an existing reference key ($artifactId)', ({ artifactId, referenceId, scopeVersion }) => {
    const reference = buildEvidenceReference(
      { artifactId, versionNo: 3, referenceId, referenceKey: 'new-slot' },
      {
        artifactId,
        name: 'evidence.pdf',
        categoryCode: 'SITE_SURVEY_EVIDENCE',
        ownerContext: 'SOL',
        lifecycleStatus: 'ACTIVE',
        artifactVersion: 7,
        reference: {
          referenceId,
          artifactId,
          versionNo: 3,
          ownerContext: 'SOL',
          objectType: 'SITE_SURVEY_ITEM',
          objectId: '2',
          purposeCode: 'SITE_SURVEY_EVIDENCE',
          referenceKey: 'server-slot',
          sensitivityCode: 'INTERNAL',
          status: 'ACTIVE',
          scopeVersion,
          referenceVersion: 8,
          createdAt: '',
          updatedAt: ''
        },
        allowedActions: [],
        createdAt: ''
      },
      {
        id: 31,
        versionNo: 3,
        sha256: 'sha',
        sizeBytes: 1,
        mediaType: 'application/pdf',
        scanStatus: 'CLEAN',
        availabilityStatus: 'AVAILABLE',
        availabilityVersion: 6,
        createdBy: 7,
        createdAt: ''
      },
      'frozen-slot'
    )

    expect(reference).toEqual({
      artifactId,
      versionNo: 3,
      referenceKey: 'frozen-slot',
      fileFactVersion: {
        artifactVersion: 7,
        referenceVersion: 8,
        availabilityVersion: 6
      },
      scopeVersion
    })

    PreparationApi.patchItem(1, 2, 4, {
      expectedPreparationVersion: 5,
      expectedInputVersion: 6,
      expectedReadinessVersion: 7,
      expectedFormVersion: 8,
      expectedProjectVersion: 9,
      evidenceReferences: [reference]
    })
    expect(request.put).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'PATCH',
        data: expect.objectContaining({
          evidenceReferences: [
            expect.objectContaining({
              referenceKey: 'frozen-slot',
              fileFactVersion: {
                artifactVersion: 7,
                referenceVersion: 8,
                availabilityVersion: 6
              }
            })
          ]
        })
      })
    )
  })

  it('emits only fields whose values actually changed', () => {
    const patch = { expectedPreparationVersion: 1 } as PatchPreparationItemReqVO
    setChanged(patch, 'notApplicableReason', '保留原因', '保留原因')
    setChanged(patch, 'assigneeUserId', 8, 7)

    expect(patch).toEqual({ expectedPreparationVersion: 1, assigneeUserId: 8 })
  })

  it('reuses a key after an unknown response and rotates it after success or changed intent', () => {
    let sequence = 0
    const keys = createIntentKeyStore(() => `key-${++sequence}`)
    const firstIntent = intentOf('CREATE_WAIVER', { itemId: 2, reason: 'same' })

    expect(keys.key(firstIntent)).toBe('key-1')
    expect(keys.key(firstIntent)).toBe('key-1')
    expect(keys.key(intentOf('CREATE_WAIVER', { itemId: 2, reason: 'changed' }))).toBe('key-2')
    keys.complete(firstIntent)
    expect(keys.key(firstIntent)).toBe('key-3')
  })
})
