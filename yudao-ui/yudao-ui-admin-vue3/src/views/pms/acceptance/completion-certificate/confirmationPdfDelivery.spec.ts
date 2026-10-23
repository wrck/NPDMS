import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reuseConfirmationPdf, pdfDigest, confirmationPdfIdentity } from './confirmationPdfDelivery'
import { getRegisteredConfirmationPdf, getConfirmationFileTicket } from '@/api/pms/acceptance/completion-certificate'
vi.mock('@/api/pms/acceptance/completion-certificate', () => ({ getRegisteredConfirmationPdf: vi.fn(), getConfirmationFileTicket: vi.fn() }))
const certificate = { id: 10, projectId: 9, name: '项目', version: 3, completionDate: '2026-10-13' }
const layoutKey = 'a'.repeat(32)
const fetchFile = vi.fn()
beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('fetch', fetchFile) })
afterEach(() => vi.unstubAllGlobals())
describe('PDF lookup before generation', () => {
  it('works on HTTP without Web Crypto', async () => {
    vi.stubGlobal('crypto', undefined)
    expect(await pdfDigest(new TextEncoder().encode('abc').buffer)).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
  })
  it('queries by record, version, kind and template identity without generating a file', async () => {
    vi.mocked(getRegisteredConfirmationPdf).mockResolvedValue({ materialId: 20 })
    vi.mocked(getConfirmationFileTicket).mockResolvedValue('/controlled-file')
    const blob = new Blob(['existing PDF'])
    fetchFile.mockResolvedValue({ ok: true, blob: async () => blob })
    expect(await reuseConfirmationPdf({ certificate, kind: 'COMPLETION', layoutKey })).toBe(blob)
    expect(getRegisteredConfirmationPdf).toHaveBeenCalledWith({ id: 10, expectedVersion: 3, kind: 'COMPLETION', layoutKey })
    expect(getConfirmationFileTicket).toHaveBeenCalledWith(10, 20)
  })
  it('allows generation only when no registered file exists', async () => {
    vi.mocked(getRegisteredConfirmationPdf).mockResolvedValue(null)
    expect(await reuseConfirmationPdf({ certificate, kind: 'SATISFACTION', layoutKey })).toBeUndefined()
    expect(fetchFile).not.toHaveBeenCalled()
  })
  it('does not turn download failure into permission to generate another file', async () => {
    vi.mocked(getRegisteredConfirmationPdf).mockResolvedValue({ materialId: 20 })
    vi.mocked(getConfirmationFileTicket).mockResolvedValue('/controlled-file')
    fetchFile.mockResolvedValue({ ok: false })
    await expect(reuseConfirmationPdf({ certificate, kind: 'COMPLETION', layoutKey })).rejects.toThrow('暂不可下载')
  })
  it('has stable identity independent of a rendered image and changes with the template or record', async () => {
    const first = await confirmationPdfIdentity({ layout: { revision: 1 }, certificate })
    expect(await confirmationPdfIdentity({ layout: { revision: 1 }, certificate })).toBe(first)
    expect(await confirmationPdfIdentity({ layout: { revision: 2 }, certificate })).not.toBe(first)
    expect(await confirmationPdfIdentity({ layout: { revision: 1 }, certificate: { ...certificate, version: 4 } })).not.toBe(first)
  })
})
