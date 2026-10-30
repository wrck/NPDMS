import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Panel from './BaseSyncPanel.vue'

const api = vi.hoisted(() => ({ getRegistrations: vi.fn(), getTasks: vi.fn(), run: vi.fn() }))
vi.mock('@/api/pms/integration/base-sync', () => api)
const flush = async () => { for (let i = 0; i < 15; i++) await nextTick() }
beforeEach(() => {
  vi.resetAllMocks()
  api.getRegistrations.mockResolvedValue([{ id: 15, taskCode: 'BASE_P15', batchSize: 100, maxRows: 1000 }])
  api.getTasks.mockResolvedValue([{ code: 'BASE_P15', name: '发货同步' }])
  api.run.mockResolvedValue({ read: 1, inserted: 0, updated: 0, unchanged: 1, deleted: 0 })
})
it('defaults P15 to all supported stages and preserves a selected subset after refresh', async () => {
  const view = mount(Panel, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElDescriptions: passthrough, ElDescriptionsItem: passthrough })
  try {
    await flush()
    const state = (view.vm as any).$.setupState
    expect(state.rows[0].inputStages).toHaveLength(9)
    state.rows[0].inputStages = ['OrderContractRelation']
    await state.submit(state.rows[0], true)
    expect(api.run).toHaveBeenCalledWith({ preview: true, taskId: 15, batchSize: 100, stages: ['OrderContractRelation'] })
    expect(state.rows[0].inputStages).toEqual(['OrderContractRelation'])
  } finally { view.app.unmount() }
})
it('rejects empty P15 stages without issuing a run request', async () => {
  const view = mount(Panel, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElDescriptions: passthrough, ElDescriptionsItem: passthrough })
  try {
    await flush()
    const state = (view.vm as any).$.setupState
    state.rows[0].inputStages = []
    await state.submit(state.rows[0], false)
    expect(api.run).not.toHaveBeenCalled()
    expect(state.error).toContain('至少选择一个')
  } finally { view.app.unmount() }
})
it('keeps a failed run uncertain and refreshes registration instead of reporting completion', async () => {
  api.run.mockRejectedValue(new Error('disconnected'))
  const view = mount(Panel, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElDescriptions: passthrough, ElDescriptionsItem: passthrough })
  try {
    await flush()
    const state = (view.vm as any).$.setupState
    await state.submit(state.rows[0], false)
    expect(state.result).toBeUndefined()
    expect(state.error).toContain('核对任务状态')
    expect(state.busy).toBe(false)
    expect(api.getRegistrations).toHaveBeenCalledTimes(2)
  } finally { view.app.unmount() }
})
