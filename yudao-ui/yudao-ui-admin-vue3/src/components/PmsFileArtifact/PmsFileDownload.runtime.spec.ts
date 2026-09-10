import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import PmsFileReferenceList from './PmsFileReferenceList.vue'
import * as FileApi from '@/api/pms/platform/file'
import download from '@/utils/download'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/platform/file', () => ({ getArtifact: vi.fn(), createAccessTicket: vi.fn() }))
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
    vi.stubGlobal('window', { open: vi.fn() })
    vi.stubGlobal('fetch', vi.fn())
    vi.mocked(FileApi.getArtifact).mockResolvedValue({ artifactId: 9, name: '工勘附件.txt', categoryCode: 'DYNAMIC_FORM_ATTACHMENT', reference: { ...key, versionNo: 1, status: 'ACTIVE' }, allowedActions: ['PREVIEW', 'DOWNLOAD'] } as any)
    vi.mocked(FileApi.createAccessTicket).mockResolvedValue({ shortLivedUrl: 'https://storage.test/signed-file' } as any)
  })
  afterEach(() => vi.unstubAllGlobals())

  const setup = async () => {
    const mounted = mount(PmsFileReferenceList, { ...key, artifactId: 9, versionNo: 2 })
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

  it('keeps preview navigation and does not download preview bytes', async () => {
    const popup = { opener: {}, location: { replace: vi.fn() }, close: vi.fn() }
    vi.mocked(window.open).mockReturnValue(popup as any)
    const mounted = await setup()
    try {
      await mounted.click('预览')
      expect(FileApi.createAccessTicket).toHaveBeenCalledWith(9, 2, 'PREVIEW', key)
      expect(popup.opener).toBeNull()
      expect(popup.location.replace).toHaveBeenCalledWith('https://storage.test/signed-file')
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
})
