import { nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Drawer from './ProjectDeviceConfigLogDrawer.vue'
import * as Archive from '@/api/pms/asset/device-log-archive'
import * as Device from '@/api/pms/asset/device'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/asset/device-log-archive', () => ({ history: vi.fn(), parseState: vi.fn() }))
vi.mock('@/api/pms/asset/device', () => ({ getConfigurationLogs: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
beforeEach(() => { vi.clearAllMocks(); vi.mocked(Archive.parseState).mockResolvedValue({ state: 'SUCCEEDED' }) })
it('shows fast archive rows while legacy is pending and retains errors from the slow source', async () => {
  vi.mocked(Archive.history).mockResolvedValue([{ id: 1, fileName: 'one.log', available: false }] as any)
  let rejectLegacy!: (reason: Error) => void
  vi.mocked(Device.getConfigurationLogs).mockImplementation(() => new Promise((_, reject) => { rejectLegacy = reject }))
  const m = mount(Drawer, { projectId: 7 }, { ElDrawer: passthrough, ElTable: passthrough, ElTableColumn: tableColumn })
  const state = (m.vm as any).$.setupState
  try {
    state.open(10, 'SN10'); await flush()
    expect(state.rows).toHaveLength(1)
    expect(state.loading).toBe(false)
    expect(state.pendingSources).toBe(1)
    rejectLegacy(new Error('legacy unavailable')); await flush()
    expect(state.rows).toHaveLength(1)
    expect(state.pendingSources).toBe(0)
    expect(state.errors).toContain('历史配置日志加载失败，请刷新重试')
  } finally { m.app.unmount() }
})
it('does not append the previous device slow response after switching devices', async () => {
  let resolveOld!: (value: any[]) => void
  vi.mocked(Archive.history).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    .mockResolvedValueOnce([{ id: 2, fileName: 'two.log', available: false }] as any)
  vi.mocked(Device.getConfigurationLogs).mockResolvedValue([])
  const m = mount(Drawer, { projectId: 7 }, { ElDrawer: passthrough, ElTable: passthrough, ElTableColumn: tableColumn })
  const state = (m.vm as any).$.setupState
  try {
    state.open(10, 'SN10'); await flush()
    state.open(20, 'SN20'); await flush()
    resolveOld([{ id: 1, fileName: 'one.log' }]); await flush()
    expect(state.rows.map((row: any) => row.name)).toEqual(['two.log'])
    expect(state.pendingSources).toBe(0)
  } finally { m.app.unmount() }
})
