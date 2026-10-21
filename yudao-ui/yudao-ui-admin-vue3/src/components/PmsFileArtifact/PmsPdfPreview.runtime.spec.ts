import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import PdfPreview from './PmsPdfPreview.vue'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const mocks = vi.hoisted(() => ({ getDocument: vi.fn(), getPage: vi.fn(), render: vi.fn(), cancel: vi.fn(), destroy: vi.fn() }))
vi.mock('pdfjs-dist', () => ({ GlobalWorkerOptions: {}, getDocument: mocks.getDocument }))
vi.mock('@vueuse/core', () => ({ useResizeObserver: vi.fn() }))
const flush = async () => { for (let i = 0; i < 16; i++) { await Promise.resolve(); await nextTick() } }
const findButton = (node: TestNode, label: string): TestNode | undefined => {
  if (node.type === 'button' && textOf(node) === label) return node
  for (const child of node.children) { const found = findButton(child, label); if (found) return found }
}

describe('PDF renderer lifecycle', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('window', { devicePixelRatio: 1 })
    mocks.render.mockImplementation(() => ({ promise: Promise.resolve(), cancel: mocks.cancel }))
    mocks.getPage.mockResolvedValue({ getViewport: ({ scale }: { scale: number }) => ({ width: 600 * scale, height: 800 * scale }), render: mocks.render })
    mocks.getDocument.mockReturnValue({ promise: Promise.resolve({ numPages: 2, getPage: mocks.getPage }), destroy: mocks.destroy })
  })
  afterEach(() => vi.unstubAllGlobals())

  it('renders authenticated bytes locally, pages through the document and cleans up on unmount', async () => {
    const page = mount(PdfPreview, { file: new Blob(['%PDF']) })
    try {
      await flush()
      expect(mocks.getDocument).toHaveBeenCalledWith(expect.objectContaining({ data: expect.any(Uint8Array) }))
      expect(textOf(page.root)).toContain('第 1 / 2 页')
      expect(findButton(page.root, '上一页')?.props?.disabled).toBe(true)
      await (findButton(page.root, '下一页')!.props!.onClick as () => Promise<void>)()
      await flush()
      expect(mocks.getPage).toHaveBeenLastCalledWith(2)
      expect(textOf(page.root)).toContain('第 2 / 2 页')
      expect(findButton(page.root, '下一页')?.props?.disabled).toBe(true)
    } finally { page.app.unmount() }
    expect(mocks.cancel).toHaveBeenCalled()
    expect(mocks.destroy).toHaveBeenCalledOnce()
  })

  it('reports a document decoding failure for the parent retry state', async () => {
    mocks.getDocument.mockReturnValue({ promise: Promise.reject(new Error('invalid document')), destroy: mocks.destroy })
    const error = vi.fn()
    const page = mount(PdfPreview, { file: new Blob(['invalid']), onError: error })
    try { await flush(); expect(error).toHaveBeenCalledOnce(); expect(mocks.render).not.toHaveBeenCalled() }
    finally { page.app.unmount() }
  })

  it('destroys a pending document on close and never renders a late response', async () => {
    let resolve!: (document: unknown) => void
    mocks.getDocument.mockReturnValue({ promise: new Promise(done => { resolve = done }), destroy: mocks.destroy })
    const page = mount(PdfPreview, { file: new Blob(['%PDF']) })
    await flush()
    page.app.unmount()
    resolve({ numPages: 2, getPage: mocks.getPage }); await flush()
    expect(mocks.destroy).toHaveBeenCalledOnce()
    expect(mocks.render).not.toHaveBeenCalled()
  })
})
