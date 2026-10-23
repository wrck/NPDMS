import { beforeEach, expect, it, vi } from 'vitest'
import { createArrivalUploadAttempt, saveArrivalReceipt } from './saveArrivalReceipt'
const api = vi.hoisted(() => ({ createArrival: vi.fn(), getArrival: vi.fn(), updateArrival: vi.fn(), signArrival: vi.fn(), initializeUpload: vi.fn(), completeUpload: vi.fn() }))
vi.mock('@/api/pms/engineering/arrival', () => api)
vi.mock('@/api/pms/platform/file', () => api)
vi.mock('@/utils', () => ({ generateUUID: () => 'attempt-key' }))
const file = { name: '签收.txt', size: 3, type: 'text/plain' } as File
const pending = { id: 7, projectId: 123, status: 0, version: 0, attachmentUrl: '' }
const attached = { ...pending, signDocumentArtifactId: 11, signDocumentReferenceVersion: 1 }
beforeEach(() => {
  vi.resetAllMocks()
  api.createArrival.mockResolvedValue(7)
  api.initializeUpload.mockResolvedValue({ artifactId: 11, sessionId: 22 })
  api.completeUpload.mockResolvedValue({ artifactId: 11, referenceId: 33, versionNo: 1 })
})
it('uploads under the saved arrival owner and uses the native sign command after material registration', async () => {
  api.getArrival.mockResolvedValueOnce(pending).mockResolvedValueOnce(attached).mockResolvedValueOnce({ ...attached, version: 1 }).mockResolvedValueOnce({ ...attached, status: 1, version: 2 })
  const form = { projectId: 123, attachmentUrl: '' }
  await saveArrivalReceipt(form, createArrivalUploadAttempt(file))
  expect(api.createArrival).toHaveBeenCalledWith(expect.objectContaining({ attachmentUrl: '' }))
  expect(api.initializeUpload).toHaveBeenCalledWith(expect.objectContaining({ ownerContext: 'IMP', objectType: 'ARRIVAL', objectId: '7', purposeCode: 'ARRIVAL_SIGN_DOCUMENT' }), 'attempt-key')
  expect(api.updateArrival).toHaveBeenCalledWith(expect.objectContaining({ version: 0, arrivalTime: expect.any(Number) }))
  expect(api.signArrival).toHaveBeenCalledWith(7)
  expect(form).toMatchObject({ id: 7, status: 1 })
})
it('retains a failed upload session and reuses it without creating a second receipt', async () => {
  const form = { projectId: 123 }
  const attempt = createArrivalUploadAttempt(file)
  api.getArrival.mockResolvedValue(pending)
  api.completeUpload.mockRejectedValueOnce(new Error('上传失败'))
  await expect(saveArrivalReceipt(form, attempt)).rejects.toThrow('上传失败')
  expect(api.signArrival).not.toHaveBeenCalled()
  api.getArrival.mockResolvedValueOnce(attached).mockResolvedValueOnce(attached).mockResolvedValueOnce({ ...attached, status: 1 })
  await saveArrivalReceipt(form, attempt)
  expect(api.createArrival).toHaveBeenCalledTimes(1)
  expect(api.initializeUpload).toHaveBeenCalledTimes(1)
  expect(api.completeUpload.mock.calls[0].slice(0, 4)).toEqual(api.completeUpload.mock.calls[1].slice(0, 4))
})
it('reuses a completed file when sign failed, and does not repeat a sign whose response was lost', async () => {
  const form = { ...pending }
  const attempt = createArrivalUploadAttempt(file)
  api.getArrival.mockResolvedValue(attached)
  api.signArrival.mockRejectedValueOnce(new Error('签收失败'))
  await expect(saveArrivalReceipt(form, attempt)).rejects.toThrow('签收失败')
  api.getArrival.mockResolvedValue({ ...attached, status: 1 })
  await saveArrivalReceipt(form, attempt)
  expect(api.completeUpload).toHaveBeenCalledTimes(1)
  expect(api.signArrival).toHaveBeenCalledTimes(1)
})
it('requires a real attached document and preserves the abnormal-record lifecycle', async () => {
  api.getArrival.mockResolvedValue(pending)
  await expect(saveArrivalReceipt({ ...pending })).rejects.toThrow('签收单上传尚未完成')
  expect(api.updateArrival).not.toHaveBeenCalled()
  api.getArrival.mockResolvedValue({ ...attached, status: 2 })
  await saveArrivalReceipt({ ...attached, status: 2 })
  expect(api.updateArrival).toHaveBeenCalled()
  expect(api.signArrival).not.toHaveBeenCalled()
})
