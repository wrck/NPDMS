import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as FileApi from './index'

const client = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: client }))
const key = { ownerContext: 'SOL', objectType: 'NATIVE_OWNER', objectId: '9007199254740997', purposeCode: 'EVIDENCE', referenceKey: 'fixed-slot' }

describe('file HTTP identities', () => {
  beforeEach(() => vi.resetAllMocks())
  it('discovers a reference without inventing an artifact ID', () => {
    FileApi.getReference(key)
    expect(client.get).toHaveBeenCalledWith({ url: '/api/v1/pms/file-references', params: key })
  })
  it('keeps large string IDs exact in URLs and multipart fields', () => {
    FileApi.completeUpload('9007199254740993', '9007199254740995', new File(['data'], 'a.txt'), 'key')
    const call = client.post.mock.calls[0][0]
    expect(call.url).toBe('/api/v1/pms/files/9007199254740993:complete-upload')
    expect(call.data.get('sessionId')).toBe('9007199254740995')
    FileApi.detachReference('9007199254740997', 3, key, 'reason', 'detach')
    expect(client.delete.mock.calls[0][0].url).toBe('/api/v1/pms/file-references/9007199254740997')
  })
  it('rejects already rounded numeric IDs before issuing any request', () => {
    expect(() => FileApi.getArtifact(Number('9007199254740993'), key)).toThrow()
    expect(() => FileApi.completeUpload('9007199254740993', Number('9007199254740995'), new File(['x'], 'a.txt'), 'key')).toThrow()
    expect(client.get).not.toHaveBeenCalled()
    expect(client.post).not.toHaveBeenCalled()
  })
})
