import { expect, it, vi } from 'vitest'
import { useCollectionEvidence } from './use-collection-evidence'
const api = vi.hoisted(() => ({ getCollectionEvidence: vi.fn() }))
vi.mock('@/api/collection-evidence', () => api)
const location = { collectionId: 'one', namespace: 'ns', mode: 'generic' as const }
it('aborts and ignores late old evidence success and failure', async () => {
  let resolve!: (value: unknown) => void
  api.getCollectionEvidence.mockReturnValueOnce(new Promise(yes => { resolve = yes })).mockResolvedValueOnce({ metadata: { collectionId: 'two' } })
  const state = useCollectionEvidence()
  const old = state.load(location)
  const signal = api.getCollectionEvidence.mock.calls[0]![1] as AbortSignal
  await state.load({ ...location, collectionId: 'two' })
  expect(signal.aborted).toBe(true)
  resolve({ metadata: { collectionId: 'one' } }); await old
  expect(state.evidence.value?.metadata.collectionId).toBe('two')
  state.reset(); expect(state.evidence.value).toBeUndefined()
})
it('ignores stale failure and disposed in-flight evidence', async () => {
  let reject!: (error: unknown) => void
  api.getCollectionEvidence.mockReturnValueOnce(new Promise((_, no) => { reject = no })).mockResolvedValueOnce({ metadata: { collectionId: 'two' } })
  const state = useCollectionEvidence(); const old = state.load(location)
  await state.load({ ...location, collectionId: 'two' })
  reject(new Error('stale')); await old
  expect(state.status.value).toBe('READY')
  let resolve!: (value: unknown) => void
  api.getCollectionEvidence.mockReturnValueOnce(new Promise(yes => { resolve = yes }))
  const disposed = state.load(location); state.reset()
  resolve({ metadata: { collectionId: 'late' } }); await disposed
  expect(state.evidence.value).toBeUndefined()
  expect(state.status.value).toBe('IDLE')
})
it('maps forbidden evidence to restricted without assuming input exists', async () => {
  api.getCollectionEvidence.mockRejectedValueOnce({ isAxiosError: true, response: { status: 403 } })
  const state = useCollectionEvidence(); await state.load(location)
  expect(state.status.value).toBe('RESTRICTED')
  expect(state.evidence.value).toBeUndefined()
})
