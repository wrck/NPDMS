import { describe, expect, it, vi, beforeEach } from 'vitest'
import request from '@/config/axios'
import { getBusinessOperationCatalog, resolveBusinessOperation } from '@/api/pms/project/project-templates/operations'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())

describe('permission-based operation configuration API', () => {
  it('preserves the existing catalog query', async () => {
    vi.mocked(request.get).mockResolvedValue([])
    await getBusinessOperationCatalog('SOL', 'SITE_SURVEY')
    expect(request.get).toHaveBeenCalledWith({ url: '/api/v1/pms/project-templates/operation-catalog',
      params: { ownerContext: 'SOL', objectType: 'SITE_SURVEY' } })
  })
  it('sends permission and optional existing operation, not a user-defined version combination', async () => {
    vi.mocked(request.get).mockResolvedValue({ status: 'RESOLVED', selected: { operationCode: 'CONFIRM' }, candidates: [] })
    await resolveBusinessOperation('SOL', 'SITE_SURVEY', 'pms:eng-site-survey:update', 'SOL.SITE_SURVEY.CONFIRM')
    expect(request.get).toHaveBeenCalledWith({ url: '/api/v1/pms/project-templates/operation-catalog/resolve',
      params: { ownerContext: 'SOL', objectType: 'SITE_SURVEY', permissionCode: 'pms:eng-site-survey:update',
        operationCode: 'SOL.SITE_SURVEY.CONFIRM' } })
  })
  it('does not select the first candidate for ambiguous permissions or versions', async () => {
    for (const status of ['AMBIGUOUS_OPERATION', 'AMBIGUOUS_VERSION']) {
      const response = { status, selected: null, candidates: [{ operationCode: 'A' }, { operationCode: 'B' }] }
      vi.mocked(request.get).mockResolvedValue(response)
      expect(await resolveBusinessOperation('SOL', 'SITE_SURVEY', 'write')).toBe(response)
    }
  })
  it('propagates lookup failure rather than using a legacy command or alternate endpoint', async () => {
    vi.mocked(request.get).mockRejectedValue(new Error('unavailable'))
    await expect(resolveBusinessOperation('SOL', 'SITE_SURVEY', 'write')).rejects.toThrow('unavailable')
    expect(request.get).toHaveBeenCalledTimes(1)
  })
})
