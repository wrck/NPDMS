import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import PmsFileReferenceList from './PmsFileReferenceList.vue'
import * as FileApi from '@/api/pms/platform/file'
import download from '@/utils/download'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/platform/file', () => ({ getArtifact: vi.fn(), createAccessTicket: vi.fn(), getPreviewContent: vi.fn() }))
vi.mock('@/components/PmsFileArtifact/PmsPdfPreview.vue', () => ({ default: { render: () => null } }))
vi.mock('@/utils/download', () => ({ default: { file: vi.fn() } }))
vi.mock('./PmsFileVersionDrawer.vue', () => ({ default: { render: () => null } }))
const key = { ownerContext: 'PLATFORM', objectType: 'DYNAMIC_FORM_INSTANCE', objectId: '32', purposeCode: 'FORM_FIELD_ATTACHMENT/PROJECT_BACKGROUND', referenceKey: 'slot-1' }
const findButton = (node: TestNode, label: string): TestNode | undefined => {
  if (node.type === 'button' && textOf(node) === label) return node
  for (const child of node.children) { const found = findButton(child, label); if (found) return found }
}
const flush = async () => { for (let i = 0; i < 6; i++) await nextTick() }

describe('controlled file access uses the requested operation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('window', { open: vi.fn(), setInterval: vi.fn(), clearInterval: vi.fn() })
    vi.mocked(FileApi.getPreviewContent).mockResolvedValue(new Blob(['%PDF-test'], { type: 'application/pdf' }))
    vi.stubGlobal('fetch', vi.fn())
    vi.mocked(FileApi.getArtifact).mockResolvedValue({ artifactId: 9, name: '工勘附件.txt', categoryCode: 'DYNAMIC_FORM_ATTACHMENT', reference: { ...key, versionNo: 1, status: 'ACTIVE' }, allowedActions: ['PREVIEW', 'DOWNLOAD'] } as any)
    vi.mocked(FileApi.createAccessTicket).mockResolvedValue({ shortLivedUrl: 'https://storage.test/signed-file' } as any)
  })
  afterEach(() => vi.unstubAllGlobals())

  const setup = async () => {
    const mounted = mount(PmsFileReferenceList, { ...key, artifactId: 9, versionNo: 2 }, { ElDialog: passthrough })
    await flush()
    const click = (label: string) => (findButton(mounted.root, label)!.props!.onClick as () => Promise<void>)()
    return { ...mounted, click }
  }

  it('downloads authorized bytes with the original name and exact version, without forwarding app credentials', async () => {
    const file = new Blob(['synthetic fixture'], { type: 'text/plain' })
    vi.mocked(fetch).mockResolvedValue({ ok: true, blob: async () => file } as Response)
    const mounted = await setup()
    try {
      await mounted.click('下载')
      expect(FileApi.createAccessTicket).toHaveBeenCalledWith(9, 2, 'DOWNLOAD', key)
      expect(fetch).toHaveBeenCalledWith('https://storage.test/signed-file', { credentials: 'omit' })
      expect(download.file).toHaveBeenCalledWith(file, '工勘附件.txt')
      expect(window.open).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('previews the exact version inside a dialog without opening a tab or downloading bytes', async () => {
    const mounted = await setup()
    try {
      await mounted.click('预览')
      expect(FileApi.getPreviewContent).toHaveBeenCalledWith(9, 2, key)
      expect(FileApi.createAccessTicket).not.toHaveBeenCalled()
      await flush()
      expect(window.open).not.toHaveBeenCalled()
      expect(textOf(mounted.root)).toContain('工勘附件.txt · V2')
      expect(fetch).not.toHaveBeenCalled()
      expect(download.file).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('does not fetch or download when the owner rejects the access ticket', async () => {
    vi.mocked(FileApi.createAccessTicket).mockRejectedValue(new Error('permission denied'))
    const mounted = await setup()
    try {
      await mounted.click('下载'); await flush()
      expect(fetch).not.toHaveBeenCalled()
      expect(download.file).not.toHaveBeenCalled()
      expect(textOf(mounted.root)).toContain('文件下载未完成')
    } finally { mounted.app.unmount() }
  })

  it('keeps a failed preview in the dialog and retries the same exact document version', async () => {
    vi.mocked(FileApi.getPreviewContent).mockRejectedValueOnce(new Error('conversion failed'))
    const mounted = await setup()
    try {
      await mounted.click('预览'); await flush()
      expect(window.open).not.toHaveBeenCalled()
      expect(textOf(mounted.root)).toContain('文件预览未完成')
      await mounted.click('重试预览'); await flush()
      expect(FileApi.getPreviewContent).toHaveBeenCalledTimes(2)
      expect(FileApi.getPreviewContent).toHaveBeenLastCalledWith(9, 2, key)
      expect(textOf(mounted.root)).not.toContain('文件预览未完成')
      expect(download.file).not.toHaveBeenCalled()
    } finally { mounted.app.unmount() }
  })

  it('does not save a storage error response as a successful file and allows a fresh-ticket retry', async () => {
    const file = new Blob(['retry file'])
    vi.mocked(fetch).mockResolvedValueOnce({ ok: false } as Response).mockResolvedValueOnce({ ok: true, blob: async () => file } as Response)
    const mounted = await setup()
    try {
      await mounted.click('下载'); await flush()
      expect(download.file).not.toHaveBeenCalled()
      expect(textOf(mounted.root)).toContain('文件下载未完成')
      await mounted.click('下载'); await flush()
      expect(FileApi.createAccessTicket).toHaveBeenCalledTimes(2)
      expect(download.file).toHaveBeenCalledWith(file, '工勘附件.txt')
      expect(textOf(mounted.root)).not.toContain('文件下载未完成')
    } finally { mounted.app.unmount() }
  })

  it('ignores repeated download clicks while bytes are still being retrieved', async () => {
    let resolve!: (response: Response) => void
    vi.mocked(fetch).mockImplementation(() => new Promise(r => { resolve = r }))
    const mounted = await setup()
    try {
      const pending = mounted.click('下载'); await flush()
      await mounted.click('下载')
      expect(FileApi.createAccessTicket).toHaveBeenCalledTimes(1)
      resolve({ ok: true, blob: async () => new Blob(['one download']) } as Response)
      await pending
      expect(download.file).toHaveBeenCalledTimes(1)
    } finally { mounted.app.unmount() }
  })

  it('does not unlock an active download when a closed preview finishes in the background', async () => {
    let resolvePreview!: (blob: Blob) => void
    let resolveDownload!: (response: Response) => void
    vi.mocked(FileApi.getPreviewContent).mockImplementation(() => new Promise(done => { resolvePreview = done }))
    vi.mocked(fetch).mockImplementation(() => new Promise(done => { resolveDownload = done }))
    const mounted = await setup()
    try {
      const preview = mounted.click('预览'); await flush()
      const closeDialog = (node: TestNode): boolean => {
        if (node.props?.title === '工勘附件.txt · V2') {
          ;(node.props.onClose as () => void)()
          return true
        }
        return node.children.some(closeDialog)
      }
      expect(closeDialog(mounted.root)).toBe(true)
      const downloading = mounted.click('下载'); await flush()
      resolvePreview(new Blob(['%PDF'], { type: 'application/pdf' })); await preview
      await mounted.click('下载')
      expect(FileApi.createAccessTicket).toHaveBeenCalledTimes(1)
      resolveDownload({ ok: true, blob: async () => new Blob(['file']) } as Response)
      await downloading
      expect(download.file).toHaveBeenCalledOnce()
    } finally { mounted.app.unmount() }
  })
})
