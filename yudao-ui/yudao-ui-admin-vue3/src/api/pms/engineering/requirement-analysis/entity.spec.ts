import { beforeEach, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as api from './entity'
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
it('uses independent entity endpoints and separates revision identity from concurrency version', async () => {
  const revision: api.Revision = { ref: { entity: { tenantId: '1', ownerModule: 'SOL', entityType: 'REQUIREMENT_ANALYSIS', entityId: '9007199254740993' }, revisionId: '9007199254740994' }, revisionNo: 2, state: 'DRAFT', effective: false, version: 7 }
  await api.save(revision, { values: { projectBackground: 'changed' }, expectedExtensionVersion: 0 }, 'save-key')
  expect(request.put).toHaveBeenCalledWith(expect.objectContaining({ method: 'PATCH', url: '/api/v1/pms/requirement-analyses/9007199254740993/revisions/9007199254740994', headers: { 'If-Match': '7', 'Idempotency-Key': 'save-key' } }))
  await api.complete(revision, 'complete-key'); await api.copy(revision, 'copy-key')
  expect(request.post).toHaveBeenCalledWith(expect.objectContaining({ url: expect.stringContaining('/revisions/9007199254740994/complete'), data: {}, headers: { 'If-Match': '7', 'Idempotency-Key': 'complete-key' } }))
  await api.workspace('7', '8'); await api.read(revision.ref.revisionId); await api.revisions(revision.ref.entity.entityId, '30')
  expect(request.get).toHaveBeenCalledWith({ url: '/api/v1/pms/requirement-analyses', params: { projectId: '7', stageId: '8', taskId: undefined } })
  expect(request.get).toHaveBeenCalledWith(expect.objectContaining({ url: '/api/v1/pms/requirement-analyses/revisions/9007199254740994' }))
})
