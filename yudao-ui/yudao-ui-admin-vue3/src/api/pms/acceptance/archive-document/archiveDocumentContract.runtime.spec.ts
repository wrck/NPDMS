import { beforeEach, describe, expect, it, vi } from 'vitest'
const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: http }))
import * as api from './index'
describe('native archive document wire contract', () => {
  beforeEach(() => vi.clearAllMocks())
  it('preserves historical address/business version separately from numeric optimistic version', async () => {
    http.get.mockResolvedValue({ id: 9, projectId: 20, name: 'Historic', documentUrl: '/history/a.pdf', versionNo: 'v2.3', version: 7 })
    const row = await api.getArchiveDocument(9)
    expect(row.fileUrl).toBe('/history/a.pdf'); expect(row.version).toBe('v2.3'); expect(row.versionNum).toBe(7)
    await api.updateArchiveDocument(row)
    expect(http.put).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ documentUrl: '/history/a.pdf', versionNo: 'v2.3', version: 7 }) }))
    expect(http.put.mock.calls[0][0].data).not.toHaveProperty('fileUrl')
    expect(http.put.mock.calls[0][0].data).not.toHaveProperty('versionNum')
  })
  it('maps page rows without manufacturing a missing optimistic version', async () => {
    http.get.mockResolvedValue({ total: 1, list: [{ id: 9, projectId: 20, name: 'Old', versionNo: 'v1' }] })
    const page = await api.getArchiveDocumentPage({ pageNo: 1, pageSize: 10 })
    expect(page.total).toBe(1); expect(page.list[0].version).toBe('v1'); expect(page.list[0].versionNum).toBeUndefined()
  })
})
