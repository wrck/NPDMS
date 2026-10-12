import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './index.vue'
import * as Api from '@/api/pms/asset/device-log-archive'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/asset/device-log-archive', () => ({ latest: vi.fn(), parseState: vi.fn(), history: vi.fn(), upload: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/utils', () => ({ generateUUID: () => 'operation' }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/components/PmsEntitySelect/index.vue', () => ({ default: {} }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() } }))
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(Api.parseState).mockResolvedValue({ state: 'COMPLETED' })
})
it('shows only uploaded devices with pagination and all, retaining unavailable archive history', async () => {
  const uploaded = Array.from({ length: 25 }, (_, i) => ({ deviceId: i + 1, serialNumber: `SN${i + 1}`, deviceName: '设备', latest: { id: i + 1, available: i !== 24 } }))
  vi.mocked(Api.latest).mockResolvedValue([...uploaded, { deviceId: 26, serialNumber: 'NO-LOG', deviceName: '无日志' }] as any)
  const m = mount(Panel, { projectId: 1002 }, { ElTable: passthrough, ElTableColumn: tableColumn })
  const state = (m.vm as any).$.setupState
  try {
    await flush()
    expect(state.rows).toHaveLength(25)
    expect(state.pagedRows).toHaveLength(20)
    state.pageNo = 2
    expect(state.pagedRows).toHaveLength(5)
    expect(state.pagedRows[4].latest.available).toBe(false)
    state.pageSize = 25
    state.pageNo = 1
    expect(state.pagedRows).toHaveLength(25)
    expect(state.pagedRows.some((row: any) => row.serialNumber === 'NO-LOG')).toBe(false)
    state.pageSize = 20
    await state.load()
    expect(state.pageNo).toBe(1)
    expect(state.pagedRows).toHaveLength(20)
    vi.mocked(Api.latest).mockResolvedValue([{ deviceId: 26, serialNumber: 'NO-LOG', deviceName: '无日志' }])
    await state.load()
    expect(state.rows).toHaveLength(0)
  } finally { m.app.unmount() }
})
