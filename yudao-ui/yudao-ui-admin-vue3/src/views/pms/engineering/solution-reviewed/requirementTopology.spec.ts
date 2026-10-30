import { afterEach, expect, it, vi } from 'vitest'
import type { View } from '@/api/pms/engineering/requirement-analysis/entity'
import { requirementTopologyFiles, requirementTopologyImages, requirementTopologyPurpose } from './requirementTopology'

const mocks = vi.hoisted(() => ({ preview: vi.fn(), getDocument: vi.fn() }))
vi.mock('@/api/pms/platform/file', () => ({ getPreviewContent: mocks.preview }))
vi.mock('pdfjs-dist', () => ({ GlobalWorkerOptions: {}, getDocument: mocks.getDocument }))
const source = () => ({ revision: { ref: { revisionId: '701' }, revisionNo: 3 }, attachments: [
  { key: { purposeCode: requirementTopologyPurpose }, activeFacts: [
    { artifactId: '81', versionNo: 2, referenceKey: 'diagram-a', availabilityStatus: 'AVAILABLE', referenceStatus: 'ACTIVE' },
    { artifactId: '82', versionNo: 4, referenceKey: 'diagram-b', availabilityStatus: 'AVAILABLE', referenceStatus: 'ACTIVE' },
    { artifactId: '83', versionNo: 1, referenceKey: 'removed', availabilityStatus: 'UNAVAILABLE', referenceStatus: 'DETACHED' }
  ] },
  { key: { purposeCode: 'FORM_FIELD_ATTACHMENT/OTHER' }, activeFacts: [
    { artifactId: '84', versionNo: 1, referenceKey: 'other', availabilityStatus: 'AVAILABLE', referenceStatus: 'ACTIVE' }
  ] }
] }) as unknown as View
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals() })

it('exports every page of every topology file using the exact frozen revision and reference', async () => {
  mocks.preview.mockResolvedValue(new Blob(['pdf'], { type: 'application/pdf' }))
  const rendered: number[] = []
  const cleanup = vi.fn()
  const destroy = vi.fn()
  mocks.getDocument.mockImplementation(() => ({ promise: Promise.resolve({ numPages: 2,
    getPage: async (page: number) => ({ getViewport: ({ scale }: { scale: number }) => ({ width: 800 * scale, height: 600 * scale }),
      render: () => { rendered.push(page); return { promise: Promise.resolve() } }, cleanup }) }), destroy }))
  vi.stubGlobal('document', { createElement: () => ({ width: 0, height: 0, toDataURL: () => `data:image/png;base64,page${rendered.length}` }) })
  expect(requirementTopologyFiles(source())).toHaveLength(2)
  expect(await requirementTopologyImages(source())).toHaveLength(4)
  expect(rendered).toEqual([1, 2, 1, 2])
  expect(mocks.preview).toHaveBeenNthCalledWith(1, '81', 2, {
    ownerContext: 'SOL', objectType: 'REQUIREMENT_ANALYSIS_REVISION', objectId: '701',
    purposeCode: requirementTopologyPurpose, referenceKey: 'diagram-a'
  })
  expect(cleanup).toHaveBeenCalledTimes(4)
  expect(destroy).toHaveBeenCalledTimes(2)
})

it('fails export on denied source access and does not convert later files', async () => {
  mocks.preview.mockRejectedValue(new Error('permission revoked'))
  await expect(requirementTopologyImages(source())).rejects.toThrow('permission revoked')
  expect(mocks.preview).toHaveBeenCalledTimes(1)
  expect(mocks.getDocument).not.toHaveBeenCalled()
})
it('does not silently omit an unavailable file from an active frozen topology set', async () => {
  const view = source()
  view.attachments[0].activeFacts[1].availabilityStatus = 'UNAVAILABLE'
  expect(requirementTopologyFiles(view)).toHaveLength(2)
  await expect(requirementTopologyImages(view)).rejects.toThrow('不可用拓扑附件')
  expect(mocks.preview).not.toHaveBeenCalled()
  expect(mocks.getDocument).not.toHaveBeenCalled()
})
