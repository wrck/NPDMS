import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Panel from './index.vue'
import * as Api from '@/api/pms/asset/device-log-archive'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/asset/device-log-archive', () => ({ __v_isRef: false, latest: vi.fn(), parseState: vi.fn(), history: vi.fn(), upload: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/utils', () => ({ generateUUID: () => 'operation' }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/components/PmsEntitySelect/index.vue', () => ({ default: {} }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() } }))
const uploadStub = defineComponent({ setup(_, { slots, expose }) {
  expose({ clearFiles: vi.fn() })
  return () => h('upload', slots.default?.())
} })
const render = (readonly = false) => {
  const m = mount(Panel, { projectId: 1002, readonly }, { ElTable: passthrough, ElTableColumn: tableColumn, ElUpload: uploadStub })
  return { ...m, state: (m.vm as any).$.setupState }
}
const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
it('combines log filters over the authorized snapshot and resets without hiding unavailable history', async () => {
  const file = (id: number, sourceType: Api.LogFile['sourceType']): Api.LogFile => ({
    id, sourceType, fileName: '日志.txt', importId: id, materialId: id, archivedAt: 1, available: false
  })
  vi.mocked(Api.latest).mockResolvedValue([
    { deviceId: 1, serialNumber: 'SN-001', deviceName: '核心交换机', latest: file(1, 'MANUAL_UPLOAD') },
    { deviceId: 2, serialNumber: 'SN-002', deviceName: '核心路由器', latest: file(2, 'COLLECTION_CALLBACK') },
    { deviceId: 3, serialNumber: 'SN-003', deviceName: '接入交换机' }
  ])
  const m = render()
  try {
    await flush()
    m.state.filters.serialNumber = ' sn- '; m.state.filters.deviceName = '核心'
    m.state.filters.sourceType = 'COLLECTION_CALLBACK'; m.state.search()
    expect(m.state.filteredRows.map((row: Api.DeviceLog) => row.deviceId)).toEqual([2])
    m.state.filters.deviceName = '不存在'; m.state.search()
    expect(m.state.filteredRows).toEqual([])
    m.state.resetSearch()
    expect(m.state.filteredRows).toHaveLength(3)
    expect(m.state.filteredRows[0].latest.available).toBe(false)
    expect(Api.latest).toHaveBeenCalledTimes(1)
  } finally { m.app.unmount() }
})
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(Api.latest).mockResolvedValue([])
  vi.mocked(Api.upload).mockResolvedValue({ id: 1, outcome: 'ARCHIVED', deviceCount: 1, redactedValueCount: 0, files: [] })
})

it('sends valid UTF-8 as UTF-8 under the original filename and project', async () => {
  const m = render()
  try {
    await flush()
    await m.state.uploadFile({ file: new File(['SN: SN001\n中文日志\n'], '设备.log') })
    const args = vi.mocked(Api.upload).mock.calls[0]
    expect(args[0]).toMatchObject({ projectId: 1002, operationId: 'operation' })
    expect(args[0].file.name).toBe('设备.log')
    expect(await args[0].file.text()).toBe('SN: SN001\n中文日志\n')
  } finally { m.app.unmount() }
})

it('automatically decodes GB18030/GBK and sends UTF-8 without losing Chinese or Euro bytes', async () => {
  const m = render()
  try {
    await flush()
    await m.state.uploadFile({ file: new File([new Uint8Array([0xba, 0xba, 0x80])], '多设备show tech.txt') })
    const file = vi.mocked(Api.upload).mock.calls[0][0].file
    expect(file.name).toBe('多设备show tech.txt')
    expect(new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())).toBe('汉€')
  } finally { m.app.unmount() }
})

it('rejects malformed bytes rather than replacing characters', async () => {
  const m = render()
  try {
    await flush()
    await m.state.uploadFile({ file: new File([new Uint8Array([0xff])], '设备.txt') })
    expect(Api.upload).not.toHaveBeenCalled()
    expect(m.state.uploadError).toContain('无法识别日志编码')
    expect(m.state.uploading).toBe(false)
  } finally { m.app.unmount() }
})

it('converts BOM-marked UTF-16 in either byte order and UTF-8 BOM to UTF-8 without BOM', async () => {
  const m = render()
  try {
    await flush()
    const inputs = [
      new Uint8Array([0xff, 0xfe, 0x49, 0x6c]),
      new Uint8Array([0xfe, 0xff, 0x6c, 0x49]),
      new Uint8Array([0xef, 0xbb, 0xbf, 0xe6, 0xb1, 0x89])
    ]
    for (const bytes of inputs) {
      await m.state.uploadFile({ file: new File([bytes], '设备.txt') })
      const uploaded = vi.mocked(Api.upload).mock.calls.at(-1)![0].file
      expect(new Uint8Array(await uploaded.arrayBuffer())).toEqual(new Uint8Array([0xe6, 0xb1, 0x89]))
    }
    expect(Api.upload).toHaveBeenCalledTimes(3)
  } finally { m.app.unmount() }
})

it('does not reinterpret a malformed BOM-marked file as GBK', async () => {
  const m = render()
  try {
    await flush()
    for (const bytes of [new Uint8Array([0xef, 0xbb, 0xbf, 0xba, 0xba]), new Uint8Array([0xff, 0xfe, 0x49])])
      await m.state.uploadFile({ file: new File([bytes], '设备.txt') })
    expect(Api.upload).not.toHaveBeenCalled()
    expect(m.state.uploadError).toContain('无法识别日志编码')
  } finally { m.app.unmount() }
})

it('rejects NUL binary and empty text before any upload', async () => {
  const m = render()
  try {
    await flush()
    for (const text of ['SN: SN001\0binary', '   \n'])
      await m.state.uploadFile({ file: new File([text], '设备.txt') })
    expect(Api.upload).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})

it('rechecks the UTF-8 size after converting a GBK file within the input limit', async () => {
  const m = render()
  try {
    await flush()
    const bytes = new Uint8Array(16 * 1024 * 1024).fill(0xba)
    await m.state.uploadFile({ file: new File([bytes], 'large.txt') })
    expect(Api.upload).not.toHaveBeenCalled()
    expect(m.state.uploadError).toContain('超过 20 MiB')
  } finally { m.app.unmount() }
})

it('preserves server-side association rejection and blocks readonly uploads', async () => {
  const m = render()
  try {
    await flush()
    vi.mocked(Api.upload).mockResolvedValue({ id: 2, outcome: 'REJECTED', rejectionReason: '未关联当前项目', deviceCount: 0, redactedValueCount: 0, files: [] })
    await m.state.uploadFile({ file: new File(['SN: OTHER'], 'other.txt') })
    expect(m.state.result.rejectionReason).toBe('未关联当前项目')
  } finally { m.app.unmount() }
  vi.mocked(Api.upload).mockClear()
  const readonly = render(true)
  try {
    await readonly.state.uploadFile({ file: new File(['SN: SN001'], '设备.txt') })
    expect(Api.upload).not.toHaveBeenCalled()
  } finally { readonly.app.unmount() }
})

it('retains the exact task origin through text conversion and upload', async () => {
  const m = mount(Panel, { projectId: '2110018733130579976', originTaskId: '2110018733130579999' },
    { ElTable: passthrough, ElTableColumn: tableColumn, ElUpload: uploadStub })
  try {
    await flush()
    await (m.vm as any).$.setupState.uploadFile({ file: new File(['SN: SN001'], 'task.log') })
    expect(vi.mocked(Api.upload).mock.calls[0][0]).toMatchObject({
      projectId: '2110018733130579976', originTaskId: '2110018733130579999'
    })
  } finally { m.app.unmount() }
})
