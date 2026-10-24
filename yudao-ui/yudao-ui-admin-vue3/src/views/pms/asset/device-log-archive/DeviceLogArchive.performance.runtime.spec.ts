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

beforeEach(() => vi.clearAllMocks())

it('shows the list before parsing finishes and ignores stale results after visible filters change', async () => {
  const finish = new Map<number, (value: any) => void>()
  vi.mocked(Api.latest).mockResolvedValue(Array.from({ length: 25 }, (_, index) => ({
    deviceId: index + 1, serialNumber: `SN${index + 1}`,
    latest: { id: index + 1, available: true }
  })) as any)
  vi.mocked(Api.parseState).mockImplementation(id => new Promise(resolve => finish.set(Number(id), resolve)))
  const m = mount(Panel, { projectId: 1002 }, { ElTable: passthrough, ElTableColumn: tableColumn })
  const state = (m.vm as any).$.setupState
  try {
    await vi.waitFor(() => expect(Api.parseState).toHaveBeenCalledTimes(25))
    expect(state.loading).toBe(false)
    expect(state.rows).toHaveLength(25)
    state.filters.serialNumber = 'SN25'
    state.search()
    await vi.waitFor(() => expect(Api.parseState).toHaveBeenCalledTimes(26))
    for (let id = 25; id <= 25; id++) finish.get(id)!({ state: 'SUCCEEDED' })
    for (let id = 1; id <= 24; id++) finish.get(id)!({ state: 'RUNNING' })
    await nextTick()
    await nextTick()
    expect(state.parseStates['25'].state).toBe('SUCCEEDED')
    expect(state.parseStates['1']).toBeUndefined()
  } finally { m.app.unmount() }
})

it('keeps the list usable and reports a row status when parsing lookup fails', async () => {
  vi.mocked(Api.latest).mockResolvedValue([{ deviceId: 1, serialNumber: 'SN1', latest: { id: 1, available: true } }] as any)
  vi.mocked(Api.parseState).mockRejectedValue(new Error('lookup unavailable'))
  const m = mount(Panel, { projectId: 1002 }, { ElTable: passthrough, ElTableColumn: tableColumn })
  const state = (m.vm as any).$.setupState
  try {
    await vi.waitFor(() => expect(state.parseStates['1']?.state).toBe('QUERY_FAILED'))
    expect(state.rows).toHaveLength(1)
    expect(state.loading).toBe(false)
  } finally { m.app.unmount() }
})
