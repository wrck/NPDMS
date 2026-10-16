import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { saveSolutionScripts, downloadSolutionScript, type PendingScript } from './saveSolutionScripts'
import { initializeUpload, completeUpload, createAccessTicket } from '@/api/pms/platform/file'
import { getDeliveryTypes } from '@/api/pms/platform/delivery'
vi.mock('@/api/pms/platform/file', () => ({ initializeUpload: vi.fn(), completeUpload: vi.fn(), createAccessTicket: vi.fn() }))
vi.mock('@/api/pms/platform/delivery', () => ({ getDeliveryTypes: vi.fn(), registerMaterial: vi.fn() }))
const pending = (): PendingScript[] => [{ section: 'full', file: { name: 'script.txt', size: 15 } as File }]
afterEach(() => vi.unstubAllGlobals())
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getDeliveryTypes).mockResolvedValue([{ typeCode: 'IMPLEMENTATION_PLAN', category: 'BUSINESS_DOCUMENT', maxSizeBytes: 1000 }] as any)
  vi.mocked(initializeUpload).mockResolvedValue({ artifactId: 31, sessionId: 'session' } as any)
  vi.mocked(completeUpload).mockImplementation(async () => ({ artifactId: 31, versionNo: 1, referenceId: 41, referenceKey: 'solution-script-full-key', sha256: 'digest' }))
})
it('stores the actual completed artifact version and digest under the saved solution owner', async () => {
  const result = await saveSolutionScripts({ solutionId: 42, pending: pending() })
  expect(initializeUpload).toHaveBeenCalledWith(expect.objectContaining({ objectId: 'SOL:solution:42', purposeCode: 'IMPLEMENTATION_PLAN', referenceKey: expect.stringMatching(/^solution-script-full-/) }), expect.any(String))
  expect(result[0]).toMatchObject({ referenceId: 41, artifactId: 31, versionNo: 1, sha256: 'digest', section: 'full', name: 'script.txt' })
})
it('retains session and idempotency key after interrupted byte upload', async () => {
  const items = pending()
  vi.mocked(completeUpload).mockRejectedValueOnce(new Error('interrupted'))
  await expect(saveSolutionScripts({ solutionId: 42, pending: items })).rejects.toThrow('interrupted')
  const key = items[0].attempt!.completeKey
  await saveSolutionScripts({ solutionId: 42, pending: items })
  expect(initializeUpload).toHaveBeenCalledTimes(1)
  expect(completeUpload).toHaveBeenLastCalledWith(31, 'session', items[0].file, key)
})
it('never reuses a completed or partial upload for a different solution', async () => {
  const items = pending()
  await saveSolutionScripts({ solutionId: 42, pending: items })
  await saveSolutionScripts({ solutionId: 43, pending: items })
  expect(initializeUpload).toHaveBeenCalledTimes(2)
  expect(initializeUpload).toHaveBeenLastCalledWith(expect.objectContaining({ objectId: 'SOL:solution:43' }), expect.any(String))
})
it('rejects empty files and missing owner before sending bytes', async () => {
  await expect(saveSolutionScripts({ solutionId: 0, pending: pending() })).rejects.toThrow()
  await expect(saveSolutionScripts({ solutionId: 42, pending: [{ section: 'full', file: { name: 'empty.txt', size: 0 } as File }] })).rejects.toThrow()
  expect(initializeUpload).not.toHaveBeenCalled()
})
it('reuses completed evidence after the parent solution save fails and is retried', async () => {
  const items = pending()
  const original = await saveSolutionScripts({ solutionId: 42, pending: items })
  expect(await saveSolutionScripts({ solutionId: 42, pending: items })).toEqual(original)
  expect(initializeUpload).toHaveBeenCalledTimes(1)
  expect(completeUpload).toHaveBeenCalledTimes(1)
})
it('downloads the frozen version using an authorized ticket for that solution', async () => {
  const [reference] = await saveSolutionScripts({ solutionId: 42, pending: pending() })
  vi.mocked(createAccessTicket).mockResolvedValue({ shortLivedUrl: '/temporary-download' } as any)
  vi.stubGlobal('window', { open: vi.fn() })
  await downloadSolutionScript({ solutionId: 42, reference })
  expect(createAccessTicket).toHaveBeenCalledWith(31, 1, 'DOWNLOAD', expect.objectContaining({ objectId: 'SOL:solution:42', referenceKey: reference.referenceKey }))
  expect(window.open).toHaveBeenCalledWith('/temporary-download', '_blank', 'noopener')
})
