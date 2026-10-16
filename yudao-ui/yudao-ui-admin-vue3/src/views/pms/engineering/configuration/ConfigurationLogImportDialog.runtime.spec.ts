import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Dialog from './ConfigurationLogImportDialog.vue'
import { importConfigurationLog } from '@/api/pms/engineering/configuration'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const { success, error, generateUUID } = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), generateUUID: vi.fn() }))
vi.mock('@/api/pms/engineering/configuration', () => ({ importConfigurationLog: vi.fn() }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success, error }) }))
vi.mock('@/utils', () => ({ generateUUID }))
const render = (projectId: number | string = 21) => {
  const saved = vi.fn()
  const mounted = mount(Dialog, { projectId, onSaved: saved }, {
    ElDialog: passthrough, ElForm: passthrough, ElFormItem: passthrough,
    ElUpload: passthrough, ElButton: passthrough, ElAlert: passthrough, PmsEntitySelect: passthrough
  })
  return { ...mounted, state: (mounted.vm as any).$.setupState, saved }
}
beforeEach(() => {
  vi.clearAllMocks()
  generateUUID.mockReturnValue('operation-key')
  vi.mocked(importConfigurationLog).mockResolvedValue({ importId: '50', configurationIds: ['70','71'] })
})

it('uses the current project and uploads raw log without manual device or debug fields', async () => {
  const page = render()
  try {
    page.state.open()
    const file = new File(['log'], 'devices.log')
    await page.state.upload({ file })
    expect(importConfigurationLog).toHaveBeenCalledWith(21,'operation-key',file)
    expect(page.saved).toHaveBeenCalledTimes(1)
    expect(success).toHaveBeenCalledWith('已形成2条配置记录')
    expect(page.state.visible).toBe(false)
  } finally { page.app.unmount() }
})

it('rejects empty, oversize and unsupported files but accepts the exact existing limit', () => {
  const page = render()
  try {
    expect(page.state.checkFile(new File([], 'empty.log'))).toBe(false)
    expect(page.state.checkFile(new File(['text'], 'unexpected.pdf'))).toBe(false)
    expect(page.state.checkFile(new File([new Uint8Array(20*1024*1024+1)], 'oversize.log'))).toBe(false)
    expect(page.state.checkFile(new File([new Uint8Array(20*1024*1024)], 'limit.log'))).toBe(true)
    expect(importConfigurationLog).not.toHaveBeenCalled()
  } finally { page.app.unmount() }
})

it('preserves project identifiers above the safe integer range', async () => {
  const page = render('2109371041417433096')
  try {
    page.state.open()
    const file = new File(['log'], 'devices.log')
    await page.state.upload({ file })
    expect(importConfigurationLog).toHaveBeenCalledWith('2109371041417433096', 'operation-key', file)
  } finally { page.app.unmount() }
})

it('retains the dialog and operation key after a rejected request', async () => {
  const page = render()
  try {
    page.state.open()
    const file = new File(['unrecognized'], 'missing-sn.log')
    vi.mocked(importConfigurationLog).mockRejectedValueOnce(new Error('日志块缺少设备序列号'))
    await expect(page.state.upload({ file })).rejects.toThrow('日志块缺少设备序列号')
    expect(page.state.visible).toBe(true)
    expect(page.state.uploading).toBe(false)
    expect(page.saved).not.toHaveBeenCalled()
    await page.state.upload({ file })
    expect(generateUUID).toHaveBeenCalledTimes(1)
    expect(vi.mocked(importConfigurationLog).mock.calls[1][1]).toBe('operation-key')
  } finally { page.app.unmount() }
})

it('blocks duplicate clicks during upload and emits saved only after successful import', async () => {
  const page = render()
  try {
    page.state.open()
    let finish!: (value: any) => void
    vi.mocked(importConfigurationLog).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const file = new File(['log'], 'one.log')
    const first = page.state.upload({ file })
    await nextTick()
    expect((page.vm as any).requestLeave()).toBe(false)
    await page.state.upload({ file })
    expect(importConfigurationLog).toHaveBeenCalledTimes(1)
    expect(page.saved).not.toHaveBeenCalled()
    finish({ importId: '50', configurationIds: ['70'] })
    await first
    expect((page.vm as any).requestLeave()).toBe(true)
    expect(page.saved).toHaveBeenCalledTimes(1)
  } finally { page.app.unmount() }
})
