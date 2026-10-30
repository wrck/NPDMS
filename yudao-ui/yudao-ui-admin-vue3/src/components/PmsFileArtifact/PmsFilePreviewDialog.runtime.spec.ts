import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import PreviewDialog from './PmsFilePreviewDialog.vue'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('./PmsPdfPreview.vue', () => ({ default: defineComponent({ props: { file: { type: Blob, required: true } }, setup: (props) => () => h('pdf-preview', { size: props.file.size }) }) }))

const find = (node: TestNode, type: string): TestNode | undefined => {
  if (node.type === type) return node
  for (const child of node.children) { const found = find(child, type); if (found) return found }
}
const setup = () => {
  const page = mount(PreviewDialog, {}, { ElDialog: passthrough })
  return { ...page, vm: page.vm as unknown as { open: (request: { title: string; loadContent: () => Promise<Blob> }) => Promise<void>; close: () => void } }
}

describe('shared in-page file preview', () => {
  beforeEach(() => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview-document')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  })
  afterEach(() => vi.restoreAllMocks())

  it('renders a PDF with the shared local renderer in a top-level dialog and removes it on close', async () => {
    const page = setup()
    try {
      await page.vm.open({ title: '方案.docx · V2', loadContent: async () => new Blob(['%PDF'], { type: 'application/pdf' }) })
      await nextTick()
      expect(find(page.root, 'pdf-preview')?.props?.size).toBe(4)
      expect(URL.createObjectURL).not.toHaveBeenCalled()
      expect(find(page.root, 'section')?.props?.['append-to-body']).toBe('')
      page.vm.close(); await nextTick()
      expect(find(page.root, 'pdf-preview')).toBeUndefined()
    } finally { page.app.unmount() }
  })

  it('does not reopen or allocate URLs when a closed preview finishes loading', async () => {
    let resolve!: (blob: Blob) => void
    const page = setup()
    try {
      const pending = page.vm.open({ title: '旧方案', loadContent: () => new Promise(done => { resolve = done }) })
      await nextTick()
      expect(textOf(page.root)).toContain('正在加载预览')
      page.vm.close()
      resolve(new Blob(['%PDF'], { type: 'application/pdf' })); await pending; await nextTick()
      expect(URL.createObjectURL).not.toHaveBeenCalled()
      expect(find(page.root, 'iframe')).toBeUndefined()
    } finally { page.app.unmount() }
  })

  it('allows retrying when PDF rendering fails after the bytes have loaded', async () => {
    const page = setup()
    const loadContent = vi.fn(async () => new Blob(['%PDF'], { type: 'application/pdf' }))
    try {
      await page.vm.open({ title: '方案', loadContent }); await nextTick()
      ;(find(page.root, 'pdf-preview')!.props!.onError as () => void)()
      await nextTick()
      expect(textOf(page.root)).toContain('文件预览未完成')
      expect(find(page.root, 'pdf-preview')).toBeUndefined()
      await (find(page.root, 'button')!.props!.onClick as () => Promise<void>)()
      await nextTick()
      expect(loadContent).toHaveBeenCalledTimes(2)
      expect(find(page.root, 'pdf-preview')).toBeDefined()
    } finally { page.app.unmount() }
  })

  it('keeps the newest file visible if an earlier request finishes later', async () => {
    let resolve!: (blob: Blob) => void
    const page = setup()
    try {
      const pending = page.vm.open({ title: '旧方案', loadContent: () => new Promise(done => { resolve = done }) })
      await page.vm.open({ title: '拓扑.png', loadContent: async () => new Blob(['image'], { type: 'image/png' }) })
      resolve(new Blob(['%PDF'], { type: 'application/pdf' })); await pending; await nextTick()
      expect(find(page.root, 'img')?.props?.alt).toBe('拓扑.png')
      expect(find(page.root, 'iframe')).toBeUndefined()
      expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    } finally { page.app.unmount() }
    expect(URL.revokeObjectURL).toHaveBeenCalledOnce()
  })

  it('renders plain text as text and never executes markup or navigates to HTML content', async () => {
    const page = setup()
    try {
      const text = '<script>alert(1)</script>'
      await page.vm.open({ title: '日志.txt', loadContent: async () => new Blob([text], { type: 'text/plain; charset=utf-8' }) })
      await nextTick()
      expect(textOf(find(page.root, 'pre')!)).toBe(text)
      expect(URL.createObjectURL).not.toHaveBeenCalled()
      await page.vm.open({ title: '页面.html', loadContent: async () => new Blob([text], { type: 'text/html' }) })
      await nextTick()
      expect(find(page.root, 'iframe')).toBeUndefined()
      expect(textOf(page.root)).toContain('此文件格式暂不支持在线预览')
      expect(URL.createObjectURL).not.toHaveBeenCalled()
    } finally { page.app.unmount() }
  })
})
