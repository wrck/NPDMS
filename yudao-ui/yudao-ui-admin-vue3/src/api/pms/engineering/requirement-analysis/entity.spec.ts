import { beforeEach, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as api from './entity'
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
it('uses public operation receipts and preserves exact revision identity separately from concurrency version', async () => {
  const revision: api.Revision = { ref: { entity: { tenantId: '1', ownerModule: 'SOL', entityType: 'REQUIREMENT_ANALYSIS', entityId: '9007199254740993' }, revisionId: '9007199254740994' }, revisionNo: 2, state: 'DRAFT', effective: false, version: 7 }
  const receipt = { outcome: 'SAVED', newConcurrencyBasis: 8, references: [{ kind: 'COMMAND', ownerModule: 'SOL', value: JSON.stringify({ ...revision, version: 8 }) }] }
  vi.mocked(request.post).mockResolvedValue(receipt)
  const result = await api.save(revision, { values: { projectBackground: 'changed' }, expectedExtensionVersion: 0 }, 'save-key')
  expect(result.ref.revisionId).toBe('9007199254740994')
  expect(result.operationReceipt).toBe(receipt)
  expect(request.post).toHaveBeenCalledWith(expect.objectContaining({
    url: '/api/v1/pms/business-models/SOL/requirementAnalysis/operations/save',
    params: { entityId: '9007199254740993' },
    data: expect.objectContaining({ revisionId: '9007199254740994', concurrencyBasis: 7, idempotencyKey: 'save-key',
      input: { values: { projectBackground: 'changed' }, expectedExtensionVersion: 0 } })
  }))
  await api.complete(revision, 'complete-key'); await api.copy(revision, 'copy-key')
  expect(request.put).not.toHaveBeenCalled()
  expect(request.post).toHaveBeenCalledWith(expect.objectContaining({
    url: '/api/v1/pms/business-models/SOL/requirementAnalysis/operations/complete',
    data: expect.objectContaining({ revisionId: '9007199254740994', concurrencyBasis: 7, idempotencyKey: 'complete-key' })
  }))
  await api.workspace('7', '8'); await api.read(revision.ref.revisionId); await api.revisions(revision.ref.entity.entityId, '30')
  expect(request.get).toHaveBeenCalledWith({ url: '/api/v1/pms/requirement-analyses', params: { projectId: '7', stageId: '8', taskId: undefined } })
  expect(request.get).toHaveBeenCalledWith(expect.objectContaining({ url: '/api/v1/pms/requirement-analyses/revisions/9007199254740994' }))
})
