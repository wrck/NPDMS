import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './index.vue'
import * as Api from '@/api/pms/asset/device-log-archive'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/asset/device-log-archive', () => ({ latest: vi.fn(), parseState: vi.fn(), parseResult: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/utils', () => ({ generateUUID: () => 'operation' }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/components/PmsEntitySelect/index.vue', () => ({ default: {} }))
vi.mock('@/components/DeviceCollection/LogParseResult.vue', () => ({ default: {} }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() } }))
const render = () => {
  const m = mount(Panel, { projectId: 1002 }, { ElTable: passthrough, ElTableColumn: tableColumn, ElUpload: passthrough })
  return { ...m, state: (m.vm as any).$.setupState }
}
const file = (id: number) => ({ id, fileName: `${id}.log`, available: true })
beforeEach(() => { vi.clearAllMocks(); vi.mocked(Api.latest).mockResolvedValue([]) })
it('opens the structured result for the selected latest or historical file', async () => {
  const m = render()
  try {
    const result = { state: 'SUCCEEDED', result: { taskId: 'task', semanticResult: { projections: {} } } }
    vi.mocked(Api.parseResult).mockResolvedValue(result as any)
    await m.state.viewParsed(file(91))
    expect(Api.parseResult).toHaveBeenCalledWith(91)
    expect(m.state.parsedVisible).toBe(true)
    expect(m.state.parsedResult.result.taskId).toBe('task')
    await m.state.viewParsed(file(90))
    expect(Api.parseResult).toHaveBeenCalledWith(90)
    expect(m.state.parsedName).toBe('90.log')
    expect(Api.parseState).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})
it('clears old results and shows a load error or pending message instead', async () => {
  const m = render()
  try {
    vi.mocked(Api.parseResult).mockResolvedValue({ state: 'SUCCEEDED', result: { taskId: 'old' } } as any)
    await m.state.viewParsed(file(91))
    vi.mocked(Api.parseResult).mockRejectedValue(new Error('unavailable'))
    await m.state.viewParsed(file(90))
    expect(m.state.parsedResult).toBeUndefined()
    expect(m.state.parsedError).toContain('加载失败')
    vi.mocked(Api.parseResult).mockResolvedValue({ state: 'RUNNING', message: '等待解析' })
    await m.state.viewParsed(file(92))
    expect(m.state.parsedError).toBe('')
    expect(m.state.parsedResult.message).toBe('等待解析')
  } finally { m.app.unmount() }
})
it('ignores a late response from a previously selected file', async () => {
  const m = render()
  try {
    let resolve!: (value: any) => void
    vi.mocked(Api.parseResult).mockReturnValueOnce(new Promise(r => { resolve = r }))
    const first = m.state.viewParsed(file(90))
    vi.mocked(Api.parseResult).mockResolvedValue({ state: 'SUCCEEDED', result: { taskId: 'new' } } as any)
    await m.state.viewParsed(file(91))
    resolve({ state: 'SUCCEEDED', result: { taskId: 'old' } })
    await first
    await nextTick()
    expect(m.state.parsedResult.result.taskId).toBe('new')
    expect(m.state.parsedName).toBe('91.log')
  } finally { m.app.unmount() }
})
