import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Panel from './ProjectTreePanel.vue'
const api = vi.hoisted(() => ({ queryTree: vi.fn(), moveSubtree: vi.fn(), getProjectPage: vi.fn() }))
const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn() }))
vi.mock('@/api/pms/project/projects', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_PROJECT_LIFECYCLE_STAGE: 'pms_project_lifecycle_stage' } }))
const apps: { unmount: () => void }[] = []
const flush = async () => { for (let i = 0; i < 6; i++) { await nextTick(); await Promise.resolve() } }
const render = async () => {
  const child = ref<any>()
  const { app } = mount(defineComponent({ setup: () => () => h(Panel, { projectId: 1, ref: child }) }), {},
    { ElTable: passthrough, ElTableColumn: tableColumn, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough })
  apps.push(app); await flush(); return child.value.$.setupState
}
beforeEach(() => {
  vi.clearAllMocks()
  api.queryTree.mockResolvedValue({ treeVersion: 7, items: [{ projectId: 1, projectCode: 'P1', projectName: '原项目', visibility: 'FULL' }], updating: false })
  api.moveSubtree.mockResolvedValue({ treeVersion: 8 })
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('uses the current project version and preserves retry intent', async () => {
  const state = await render()
  await state.openMove()
  expect(api.queryTree).toHaveBeenLastCalledWith(1, expect.objectContaining({ queryType: 'LOCATE' }))
  state.parentId = 2
  api.moveSubtree.mockRejectedValueOnce(new Error('response unavailable'))
  await state.submitMove()
  const first = api.moveSubtree.mock.calls[0]
  expect(first.slice(0, 3)).toEqual([1, { newParentId: 2, reason: '' }, 7])
  expect(state.moveVisible).toBe(true)
  await state.submitMove()
  expect(api.moveSubtree.mock.calls[1]).toEqual(first)
  expect(state.moveVisible).toBe(false)
})
it('loads only the current project path and direct children, keeping ancestors as context', async () => {
  api.queryTree.mockImplementation(async (id, query) => ({ treeVersion: 7, updating: false, items: query.queryType === 'LOCATE'
    ? [{ projectId: 9, projectName: '上级', visibility: 'FULL' }, { projectId: id, parentId: 9, projectName: '当前项目', visibility: 'FULL' }]
    : [{ projectId: 2, parentId: id, projectName: '子项目', visibility: 'FULL' }] }))
  const state = await render(); await flush()
  expect(api.queryTree.mock.calls.map(call => [call[0], call[1].queryType])).toEqual([[1, 'LOCATE'], [1, 'CHILDREN']])
  expect(state.rows[0].children[0].children[0].projectId).toBe(2)
  expect(state.tree.canLoadChildren(9)).toBe(false)
  await state.tree.children(9)
  expect(api.queryTree).toHaveBeenCalledTimes(2)
  expect(api.getProjectPage).not.toHaveBeenCalled()
})
it('blocks self movement and cannot submit when source-version lookup failed', async () => {
  const state = await render()
  await state.openMove(); state.parentId = 1; await state.submitMove()
  expect(api.moveSubtree).not.toHaveBeenCalled()
  api.queryTree.mockRejectedValueOnce(new Error('offline'))
  await state.openMove(); state.parentId = 2; await state.submitMove()
  expect(api.moveSubtree).not.toHaveBeenCalled()
  expect(state.moveError).toContain('offline')
})
