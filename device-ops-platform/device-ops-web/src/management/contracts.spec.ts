import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('@/api/device-ops', () => ({ deviceOpsApi: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
import { deviceOpsApi } from '@/api/device-ops'
import { parserApi } from '@/api/parser-management'
import { managementApi } from '@/api/management'
import { parseDraft, inputError, MAX_INPUT_BYTES } from './parser-input'

describe('management contracts', () => {
  beforeEach(() => { vi.resetAllMocks(); for (const method of ['get', 'post', 'put', 'delete'] as const) vi.mocked(deviceOpsApi[method]).mockResolvedValue({ data: [] }) })
  it('preserves activation compare-and-set and encoded log type', async () => {
    await parserApi.active('a/b')
    expect(deviceOpsApi.get).toHaveBeenCalledWith('parser-log-types/a%2Fb/active-release', { signal: undefined })
    await parserApi.activate('a/b', 'r2', 'r1')
    expect(deviceOpsApi.put).toHaveBeenCalledWith('parser-log-types/a%2Fb/active-release', { releaseId: 'r2', expectedCurrentReleaseId: 'r1' })
    await parserApi.revoke('a/b', 'r1')
    expect(deviceOpsApi.delete).toHaveBeenCalledWith('parser-log-types/a%2Fb/active-release', { params: { expectedCurrentReleaseId: 'r1' } })
  })
  it('uses parser cursor and management zero-based pagination', async () => {
    await parserApi.tasks('previous')
    expect(deviceOpsApi.get).toHaveBeenCalledWith('parse-tasks', { params: { limit: 20, afterTaskId: 'previous' }, signal: undefined })
    await managementApi.collections({ page: 0, size: 20 })
    expect(deviceOpsApi.get).toHaveBeenCalledWith('management/collections', { params: { page: 0, size: 20 }, signal: undefined })
  })
  it('preserves an explicit offline submission idempotency key', async () => {
    const body = { logType: 'x', inputFormat: 'command-output-block/v1', inputContent: '{}', mediaType: 'application/json', contextSnapshot: {} }
    await parserApi.submit(body, 'same-request')
    expect(deviceOpsApi.post).toHaveBeenCalledWith('parse-tasks', body, { headers: { 'Idempotency-Key': 'same-request' } })
  })
  it('requires collection ownership proof for script content', async () => {
    await managementApi.content('collection-1')
    expect(deviceOpsApi.get).toHaveBeenCalledWith('management/scripts/content', { params: { collectionId: 'collection-1' }, signal: undefined })
  })
  it('rejects malformed drafts and missing verification samples', () => {
    expect(() => parseDraft('{', 'x')).toThrow()
    expect(() => parseDraft('{"manifest":{"logType":"x"}}', 'x')).toThrow()
  })
  it('limits UTF-8 bytes and rejects blank offline input', () => {
    expect(inputError('  ')).toBeTruthy()
    expect(inputError('中'.repeat(Math.ceil(MAX_INPUT_BYTES / 3)))).toBeTruthy()
    expect(inputError('show version')).toBe('')
    expect(MAX_INPUT_BYTES).toBe(8 * 1024 * 1024)
    expect(() => parseDraft(JSON.stringify({ manifest: { logType: 'x' }, projectionsJson: 123, verificationCases: [] }), 'x')).toThrow()
  })
})
