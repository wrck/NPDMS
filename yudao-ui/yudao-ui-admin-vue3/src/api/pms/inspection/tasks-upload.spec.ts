import { expect, it, vi } from 'vitest'
import { uploadOfflineLogBatch } from './tasks'
import request from './request'

vi.mock('./request', () => ({
  default: { post: vi.fn() },
  writeHeaders: (key: string, version: string) => ({ 'Idempotency-Key': key, 'If-Match': version })
}))

it('sends all selected log parts in one request with the exact device bindings, version and retry key', () => {
  const first = new File(['one'], 'device1.txt')
  const second = new File(['two'], 'device2.log')
  const hosts = [{ deviceId: '2115280234702983169', host: '192.0.2.31' }]
  uploadOfflineLogBatch(
    '10',
    { attemptId: '20', files: [first, second], sourceHosts: hosts },
    '7',
    'stable-key'
  )
  const body = vi.mocked(request.post).mock.calls.at(-1)![0]
  const form = body.data as FormData
  expect(body.url).toBe('/api/v1/pms/inspection-tasks/10/offline-log-batches')
  expect(form.getAll('files').map((file) => (file as File).name)).toEqual([
    'device1.txt',
    'device2.log'
  ])
  expect(form.has('file')).toBe(false)
  expect(form.get('sourceHosts')).toBe(JSON.stringify(hosts))
  expect(form.get('attemptId')).toBe('20')
  expect(body.headers).toMatchObject({ 'Idempotency-Key': 'stable-key', 'If-Match': '7' })
})

it('preserves the existing platform single-file batch request', () => {
  uploadOfflineLogBatch(
    '10',
    { attemptId: '20', file: new File(['one'], 'old.log') },
    '7',
    'old-key'
  )
  const form = vi.mocked(request.post).mock.calls.at(-1)![0].data as FormData
  expect((form.get('file') as File).name).toBe('old.log')
  expect(form.has('files')).toBe(false)
})

it('omits empty bindings for multi-file single-device filename inference, preserving legacy empty-binding rejection', () => {
  const file = new File(['one'], '192.0.2.10')
  uploadOfflineLogBatch('10', { attemptId: '20', files: [file], sourceHosts: [] }, '7', 'inferred-key')
  let form = vi.mocked(request.post).mock.calls.at(-1)![0].data as FormData
  expect(form.has('sourceHosts')).toBe(false)
  const second = new File(['second'], '192.0.2.11.txt')
  uploadOfflineLogBatch('10', { attemptId: '20', files: [file, second], sourceHosts: [] }, '7', 'multi-inferred-key')
  expect(vi.mocked(request.post).mock.calls.at(-1)![0].timeout).toBe(390000)
  uploadOfflineLogBatch('10', { attemptId: '20', file, sourceHosts: [] }, '7', 'legacy-key')
  form = vi.mocked(request.post).mock.calls.at(-1)![0].data as FormData
  expect(form.get('sourceHosts')).toBe('[]')
})
