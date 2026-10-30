import { expect, it, vi } from 'vitest'
import { getCollectionEvidence } from './collection-evidence'
const get = vi.hoisted(() => vi.fn().mockResolvedValue({ data: {} }))
vi.mock('@/api/device-ops', () => ({ deviceOpsApi: { get } }))
it('uses runtime API base path once and encodes scoped identifiers', async () => {
  const signal = new AbortController().signal
  await getCollectionEvidence({ mode: 'project', projectKey: 'a/b', collectionId: 'c/d', namespace: 'ns' }, signal)
  expect(get).toHaveBeenLastCalledWith('projects/a%2Fb/collections/c%2Fd/evidence', { params: { namespace: 'ns' }, signal })
  await getCollectionEvidence({ mode: 'generic', collectionId: 'c', namespace: 'ns' }, signal)
  expect(get).toHaveBeenLastCalledWith('collections/c/evidence', { params: { namespace: 'ns' }, signal })
})
