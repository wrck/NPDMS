import { defineComponent, h, nextTick, reactive } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import ProjectReworkPanel from './ProjectReworkPanel.vue'
import * as api from '@/api/pms/project/projects/rework'
import { mount, passthrough, tableColumn, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const messages = vi.hoisted(() => ({ success: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => messages }))
vi.mock('../../project-templates/editorModel', () => ({ errorText: (error: Error) => error.message }))
vi.mock('@/api/pms/project/projects/rework', () => ({ getReworkState: vi.fn(), previewRework: vi.fn(), applyRework: vi.fn() }))
const task = { nodeKey: 'task:one', nodeKind: 'TASK' as const, code: 'T1', name: '现场工勘', stageCode: 'PREP' }
const stage = { nodeKey: 'stage:prep', nodeKind: 'STAGE' as const, code: 'PREP', name: '工前准备', stageCode: 'PREP' }
const impact: api.ReworkPreview = {
  planVersionId: '9007199254740993', projectVersion: 3,
  plan: { targets: [
    { node: task, executionId: '9007199254740994', executionVersion: 4, roundNo: 1, selected: true },
    { node: stage, executionId: '9007199254740995', executionVersion: 5, roundNo: 1, selected: false }
  ], affectedNodeKeys: ['$project'], blockers: [] }
}
const visit = (node: TestNode, match: (node: TestNode) => boolean): TestNode | undefined =>
  match(node) ? node : node.children.map(child => visit(child, match)).find(Boolean)
const tick = async () => { for (let i = 0; i < 15; i++) { await Promise.resolve(); await nextTick() } }
const update = async (root: TestNode, label: string, value: unknown) => {
  const field = visit(root, node => node.props?.['aria-label'] === label)!
  expect(field).toBeTruthy()
  ;(field.props!['onUpdate:modelValue'] as Function)(value)
  await tick()
}
const click = async (root: TestNode, label: string) => {
  const button = visit(root, node => node.type === 'button' && textOf(node) === label)!
  expect(button, label).toBeTruthy()
  expect(button.props?.disabled).not.toBe(true)
  await (button.props!.onClick as Function)()
  await tick()
}
const apps: { unmount: () => void }[] = []
const setup = async () => {
  const state = reactive({ projectId: '9', visible: true })
  const changed = vi.fn()
  const view = mount(defineComponent({ setup: () => () => h(ProjectReworkPanel, {
    projectId: state.projectId, modelValue: state.visible,
    'onUpdate:modelValue': (value: boolean) => { state.visible = value }, onChanged: changed
  }) }), {}, { ElInput: passthrough, ElCheckbox: passthrough, ElCheckboxGroup: passthrough, ElTable: passthrough, ElTableColumn: tableColumn })
  apps.push(view.app); await tick(); return { ...view, state, changed }
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.getReworkState).mockResolvedValue({ planVersionId: '9007199254740993', projectVersion: 3, nodes: [
    { node: task, executionId: '9007199254740994', executionVersion: 4, roundNo: 1, status: 'DONE' }
  ] })
  vi.mocked(api.previewRework).mockResolvedValue(impact)
  vi.mocked(api.applyRework).mockResolvedValue({ projectId: '9', planVersionId: '9007199254740993', projectVersion: 4, executions: [] })
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('separates preview from apply and preserves exact IDs and the necessary-stage concurrency tokens', async () => {
  const page = await setup()
  await update(page.root, '返工节点选择', ['task:one'])
  await click(page.root, '预览返工影响')
  expect(api.applyRework).not.toHaveBeenCalled()
  await update(page.root, '返工原因', '恢复选中任务')
  await click(page.root, '确认返工')
  expect(api.applyRework).toHaveBeenCalledWith('9', {
    planVersionId: '9007199254740993', expectedProjectVersion: 3,
    selectedNodeKeys: ['task:one'], reason: '恢复选中任务', expectedExecutions: [
      { nodeKey: 'task:one', executionId: '9007199254740994', version: 4 },
      { nodeKey: 'stage:prep', executionId: '9007199254740995', version: 5 }
    ]
  }, expect.any(String))
  expect(page.changed).toHaveBeenCalledOnce(); expect(page.state.visible).toBe(false)
})

it('keeps selection/reason and reuses the same key after an unconfirmed response', async () => {
  vi.mocked(api.applyRework).mockRejectedValue(new Error('暂未确认结果'))
  const page = await setup()
  await update(page.root, '返工节点选择', ['task:one']); await click(page.root, '预览返工影响')
  await update(page.root, '返工原因', '重试不另建一轮')
  await click(page.root, '确认返工'); await click(page.root, '确认返工')
  expect(vi.mocked(api.applyRework).mock.calls[0]).toEqual(vi.mocked(api.applyRework).mock.calls[1])
  expect(page.changed).not.toHaveBeenCalled(); expect(page.state.visible).toBe(true)
  expect(textOf(page.root)).toContain('暂未确认结果')
})

it('invalidates a preview when selection changes and never applies a blocked preview', async () => {
  const page = await setup()
  await update(page.root, '返工节点选择', ['task:one']); await click(page.root, '预览返工影响')
  await update(page.root, '返工节点选择', [])
  expect(visit(page.root, node => node.props?.['aria-label'] === '返工影响预览')).toBeUndefined()
  vi.mocked(api.previewRework).mockResolvedValue({ ...impact, plan: { ...impact.plan, blockers: [
    { nodeKey: 'task:one', code: 'REWORK_NODE_NOT_ENDED', message: '当前工作未结束' }
  ] } })
  await update(page.root, '返工节点选择', ['task:one']); await click(page.root, '预览返工影响')
  await update(page.root, '返工原因', '不可隐式终止')
  expect(visit(page.root, node => node.type === 'button' && textOf(node) === '确认返工')?.props?.disabled).toBe(true)
  expect(api.applyRework).not.toHaveBeenCalled()
})
