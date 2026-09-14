import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import {
  mount,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import ProjectTaskPanel from './ProjectTaskPanel.vue'
import type { ProjectWorkspace } from '@/api/pms/project/task-workbench'

const api = vi.hoisted(() => ({ getProjectWorkspace: vi.fn(), createTask: vi.fn() }))
const drawerProps = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/project/task-workbench', () => api)
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ warning: vi.fn(), success: vi.fn() })
}))
vi.mock('./ProjectNodeWorkbenchDrawer.vue', () => ({ default: defineComponent({
  props: { showResponsibilities: Boolean },
  setup: (props) => () => { drawerProps(props); return null }
}) }))
vi.mock('./ProjectPlanEditor.vue', () => ({
  default: defineComponent({
    props: { projectId: Number, modelValue: Boolean },
    emits: ['changed'],
    setup:
      (props, { emit }) =>
      () =>
        props.modelValue
          ? h('button', { onClick: () => emit('changed') }, `计划编辑 ${props.projectId}`)
          : null
  })
}))
vi.mock('./ProjectTaskTree.vue', () => ({
  default: defineComponent({
    props: { projectId: Number, refreshToken: Number },
    setup: (props) => () => h('span', `任务树 ${props.projectId} 刷新 ${props.refreshToken}`)
  })
}))
const apps: { unmount: () => void }[] = []
const tick = async () => {
  for (let i = 0; i < 6; i++) {
    await nextTick()
    await Promise.resolve()
  }
}
const workspace = (projectId: number, stageName: string): ProjectWorkspace => ({
  projectId,
  projectCode: `PJT${projectId}`,
  projectName: '计划改版验证',
  overviewTabs: ['tasks'],
  taskTreeVersion: 1,
  projectionWatermark: `tree:${projectId}`,
  allowedActions: [],
  stageTaskNavigation: [{ stageCode: 'PREP_WORK', stageName, stageStatus: 'ACTIVE', taskCount: 3 }]
})
const setup = async () => {
  const state = reactive({ projectId: 9, project: { id: 9, version: 1 } })
  const treeVersion = vi.fn()
  const updated = vi.fn()
  const page = mount(
    defineComponent({
      setup: () => () =>
        h(ProjectTaskPanel, {
          projectId: state.projectId,
          project: state.project,
          onUpdated: updated,
          'onTree-version': treeVersion
        })
    })
  )
  apps.push(page.app)
  await tick()
  return { ...page, state, treeVersion, updated }
}
beforeEach(() => vi.resetAllMocks())
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

const findButton = (node: TestNode, label: string): TestNode | undefined =>
  node.type === 'button' && textOf(node).trim() === label
    ? node
    : node.children.map((child) => findButton(child, label)).find(Boolean)

it('uses the same hidden responsibility default as the delivery-flow task entrance', async () => {
  api.getProjectWorkspace.mockResolvedValue(workspace(9, '工前准备'))
  await setup()
  expect(drawerProps.mock.lastCall![0].showResponsibilities).toBe(false)
})

it.each(['S0', 'CUSTOM_PREP'])(
  'opens the same plan editor for %s instead of directly creating tasks',
  async (stageCode) => {
    const result = workspace(9, '自定义阶段')
    result.stageTaskNavigation[0].stageCode = stageCode
    result.allowedActions = ['MANAGE_PLAN']
    api.getProjectWorkspace.mockResolvedValue(result)
    const page = await setup()
    await (findButton(page.root, '新建／调整任务')!.props!.onClick as Function)()
    await tick()
    expect(textOf(page.root)).toContain('计划编辑 9')
    expect(api.createTask).not.toHaveBeenCalled()
    await (findButton(page.root, '计划编辑 9')!.props!.onClick as Function)()
    await tick()
    expect(page.updated).toHaveBeenCalledOnce()
    expect(api.getProjectWorkspace).toHaveBeenCalledTimes(2)
  }
)

it('does not offer plan editing from legacy create permission alone', async () => {
  api.getProjectWorkspace.mockResolvedValue({
    ...workspace(9, '工前准备'),
    allowedActions: ['CREATE']
  })
  const page = await setup()
  expect(findButton(page.root, '新建／调整任务')).toBeUndefined()
  expect(api.createTask).not.toHaveBeenCalled()
})

it('reloads cached navigation and task tree after the project plan version changes without remounting', async () => {
  api.getProjectWorkspace
    .mockResolvedValueOnce(workspace(9, '旧阶段名称'))
    .mockResolvedValueOnce(workspace(9, '计划生效后的阶段名称'))
  const page = await setup()
  expect(textOf(page.root)).toContain('旧阶段名称')
  page.state.project.version++
  await tick()
  expect(api.getProjectWorkspace).toHaveBeenCalledTimes(2)
  expect(textOf(page.root)).toContain('计划生效后的阶段名称')
  expect(textOf(page.root)).not.toContain('旧阶段名称')
  expect(textOf(page.root)).toContain('任务树 9 刷新 1')
})

it('discards an older version response while the latest request is pending', async () => {
  let resolveOld!: (value: ProjectWorkspace) => void
  let resolveNew!: (value: ProjectWorkspace) => void
  api.getProjectWorkspace
    .mockReturnValueOnce(
      new Promise((resolve) => {
        resolveOld = resolve
      })
    )
    .mockReturnValueOnce(
      new Promise((resolve) => {
        resolveNew = resolve
      })
    )
  const page = await setup()
  page.state.project.version++
  await tick()
  resolveOld(workspace(9, '过期阶段'))
  await tick()
  expect(textOf(page.root)).not.toContain('过期阶段')
  expect(page.treeVersion).not.toHaveBeenCalled()
  resolveNew(workspace(9, '当前阶段'))
  await tick()
  expect(textOf(page.root)).toContain('当前阶段')
  expect(page.treeVersion).toHaveBeenCalledOnce()
  expect(textOf(page.root)).toContain('任务树 9 刷新 1')
})

it('does not replace the current project with a late response from the previous project', async () => {
  let resolveOld!: (value: ProjectWorkspace) => void
  api.getProjectWorkspace
    .mockReturnValueOnce(
      new Promise((resolve) => {
        resolveOld = resolve
      })
    )
    .mockResolvedValueOnce(workspace(10, '另一项目阶段'))
  const page = await setup()
  page.state.projectId = 10
  page.state.project = { id: 10, version: 1 }
  await tick()
  resolveOld(workspace(9, '旧项目阶段'))
  await tick()
  expect(api.getProjectWorkspace.mock.calls.map((call) => call[0])).toEqual([9, 10])
  expect(textOf(page.root)).toContain('另一项目阶段')
  expect(textOf(page.root)).not.toContain('旧项目阶段')
  expect(textOf(page.root)).toContain('任务树 10 刷新 1')
})
