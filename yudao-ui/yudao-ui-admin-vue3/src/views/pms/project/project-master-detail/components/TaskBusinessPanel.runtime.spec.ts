import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TaskBusinessPanel from './TaskBusinessPanel.vue'
import * as BusinessApi from '@/api/pms/project/task-business'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis/entity'
import { requirementWorkspaceKey, takeTaskViewPrefetch } from '../taskViewPrefetch'
import { getProject } from '@/api/pms/project/projects'
import { inspectOperationCapabilities } from '@/api/pms/project/execution-operations'
import { getBusinessView } from '@/api/pms/platform/business-view'
import type { TaskBusinessContext, TaskBusinessLink } from '@/api/pms/project/task-business'
import {
  mount, passthrough, textOf, type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const controls = vi.hoisted(() => ({
  confirm: vi.fn(), leave: vi.fn(), discard: vi.fn(), ownerProps: vi.fn(), ownerRefresh: vi.fn(), warning: vi.fn()
}))
vi.mock('@/components/BusinessView/ProjectMembersBusinessView.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({
    props: { project: Object, readonly: Boolean },
    setup(props, { expose }) {
      expose({ requestLeave: () => controls.leave(), refresh: vi.fn() })
      return () => h('article', `member-maintenance;readonly:${props.readonly}`)
    }
  }) }
})
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => controls }))
vi.mock('@/views/pms/cutover/task-detail/index.vue', () => ({ default: defineComponent({
  props: { taskId: String, projectId: [String, Number], readonly: Boolean, closureOnly: Boolean },
  setup(props, { expose }) {
    expose({ saveBeforeLeave: () => controls.leave() })
    return () => h('article', `cutover-maintenance:${props.taskId};closure-only:${props.closureOnly};readonly:${props.readonly}`)
  }
}) }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@/api/pms/project/task-business', () => ({
  getTaskBusinessContext: vi.fn(), getTaskBusinessCandidates: vi.fn(),
  linkTaskBusinessObject: vi.fn(), unlinkTaskBusinessObject: vi.fn(), refreshTaskBusinessResults: vi.fn()
}))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ workspace: vi.fn() }))
vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/config/axios/service', () => ({ service: {} }))
vi.mock('@/api/pms/platform/business-view', () => ({ getBusinessView: vi.fn() }))
// Keep the real Host and its two-phase target watcher. Only its Owner child/registry
// are substituted, so no private Panel functions or copied implementation are tested.
vi.mock('@/components/BusinessView/registry', async () => {
  const { defineComponent, h, ref } = await import('vue')
  const Owner = defineComponent({
    props: { registration: Object, resolvedContext: { type: Object, required: true }, allowedActions: Array, readonly: Boolean },
    setup(props, { emit, expose }) {
      const dirty = ref(false)
      expose({
        isDirty: () => dirty.value,
        refresh: () => controls.ownerRefresh(),
        requestLeave: async () => dirty.value ? await controls.leave() : true,
        discardChanges: () => {
          if (controls.discard() === false) return false
          dirty.value = false
          emit('dirty-change', false)
          return true
        }
      })
      return () => {
        controls.ownerProps(props)
        return h('article', [
          h('span', `owner:${props.resolvedContext.taskId}/${props.resolvedContext.businessObjectId};dirty:${dirty.value};readonly:${props.readonly}`),
          h('button', { onClick: () => { dirty.value = true; emit('dirty-change', true) } }, 'Owner edit'),
          h('button', { onClick: () => emit('changed') }, 'Owner changed')
        ])
      }
    }
  })
  return {
    businessViewTargetKey: (target: any) => JSON.stringify([
      target.registration.id, target.resolvedContext.taskId, target.resolvedContext.businessObjectId
    ]),
    resolveBusinessView: (target: any) => ({ component: Owner, props: target })
  }
})
const deferred = <T,>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const tick = async () => {
  for (let i = 0; i < 15; i++) { await Promise.resolve(); await nextTick() }
}
const record = (objectId = '41', id = '51'): TaskBusinessLink => ({
  id, objectId, displayName: `record-${objectId}`, factVersion: `record-fact-${objectId}`,
  allowedActions: ['QUERY', 'UPDATE', 'LINK', 'UNLINK'], completionFacts: { confirmed: true },
  artifacts: [{ artifactId: '71', versionNo: 2, referenceKey: 'source-1', displayName: 'survey.pdf', sourceVersion: '3' }]
})
const context = (taskId: string | number = '11', patch: Partial<TaskBusinessContext> = {}): TaskBusinessContext => ({
  taskId, projectId: '2099999999999999999', executionContractId: '21', contractVersion: 3,
  ownerContext: 'SOL', objectType: 'SITE_SURVEY', componentKey: 'SOL_SITE_SURVEY',
  businessViewRevisionId: '31', instanceResolutionStrategy: 'REFERENCE_EXISTING',
  links: [record()], allowedActions: ['LINK', 'UNLINK'], ownerActions: ['QUERY', 'CREATE'],
  factVersion: `fact-${taskId}`, businessView: { id: '31', componentKey: 'SOL_SITE_SURVEY' } as any,
  ...patch
})
const visit = (node: TestNode, predicate: (node: TestNode) => boolean): TestNode | undefined =>
  predicate(node) ? node : node.children.map((child) => visit(child, predicate)).find(Boolean)
const button = (root: TestNode, label: string) => visit(root, (node) => node.type === 'button' && textOf(node) === label)
const click = async (root: TestNode, label: string) => {
  const node = button(root, label)
  expect(node, label).toBeTruthy()
  await (node!.props!.onClick as () => unknown)()
  await tick()
}
const apps: { unmount: () => void }[] = []
const setup = async (initial: Record<string, unknown> = {}) => {
  const state = reactive({ taskId: '11', taskVersion: 4, readonly: false, pending: false, ...initial })
  const panel = ref<any>()
  const facts = vi.fn(), changed = vi.fn(), dirtyChanged = vi.fn()
  const mounted = mount(defineComponent({ setup: () => () => h(TaskBusinessPanel, {
    ...state, ref: panel, onFactVersion: facts, onChanged: changed, onDirtyChange: dirtyChanged
  }) }), {}, { ElSelect: passthrough, ElOption: passthrough })
  apps.push(mounted.app)
  await tick()
  await vi.dynamicImportSettled()
  await tick()
  return { ...mounted, state, panel, facts, changed, dirtyChanged }
}
beforeEach(() => {
  vi.resetAllMocks()
  controls.confirm.mockResolvedValue(undefined)
  controls.leave.mockResolvedValue(true)
  controls.discard.mockReturnValue(true)
  vi.mocked(inspectOperationCapabilities).mockImplementation(async (query) => ({
    node: { projectId: query.projectId, id: query.nodeId, kind: query.nodeKind, status: 'IN_PROGRESS' },
    reason: 'LEGACY_BINDING', actions: []
  } as any))
  vi.mocked(BusinessApi.getTaskBusinessContext).mockImplementation(async (id) => context(id))
  vi.mocked(BusinessApi.getTaskBusinessCandidates).mockResolvedValue([record('42')])
  vi.mocked(BusinessApi.linkTaskBusinessObject).mockResolvedValue({} as any)
  vi.mocked(BusinessApi.unlinkTaskBusinessObject).mockResolvedValue({} as any)
  vi.mocked(getProject).mockResolvedValue({ id: 12 } as any)
  vi.mocked(RequirementAnalysisApi.workspace).mockResolvedValue({
    projectId: 12, currentEffective: null, draft: null, allowedActions: ['QUERY']
  })
})
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  for (const taskId of ['11', '12'])
    takeTaskViewPrefetch(requirementWorkspaceKey(12, undefined, taskId))
  expect(getBusinessView).not.toHaveBeenCalled()
})

describe('PM-11 / PM-03 TaskBusinessPanel runtime', () => {
  it('keeps pending survey tasks read-only even when the Owner grants write actions', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      executionAllowed: true, ownerActions: ['QUERY', 'CREATE', 'UPDATE', 'CONFIRM'],
      allowedActions: ['REFRESH_RESULTS']
    }))
    const page = await setup({ pending: true })
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: ['QUERY'] })
    await page.panel.value.refresh()
    await tick()
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: ['QUERY'] })
    expect(BusinessApi.refreshTaskBusinessResults).not.toHaveBeenCalled()
    expect(BusinessApi.linkTaskBusinessObject).not.toHaveBeenCalled()
    expect(BusinessApi.unlinkTaskBusinessObject).not.toHaveBeenCalled()
  })
  it('keeps completed installation maintenance independent from the closed execution round', async () => {
    vi.mocked(getProject).mockResolvedValue({ id: 12, lifecycleStatus: 'ACTIVE' } as any)
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      projectId: 12, ownerContext: 'IMP', objectType: 'INSTALLATION', executionAllowed: false,
      ownerActions: ['QUERY'], allowedActions: [],
      businessView: { id: '31', componentKey: 'IMP_PROJECT_INSTALLATION', status: 'PUBLISHED' } as any
    }))
    const page = await setup({ completed: true })
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: false, allowedActions: ['QUERY'] })
    expect(controls.ownerProps.mock.lastCall![0].resolvedContext.taskId).toBeUndefined()
    expect(inspectOperationCapabilities).not.toHaveBeenCalled()
    await page.panel.value.refresh()
    expect(BusinessApi.refreshTaskBusinessResults).not.toHaveBeenCalled()
    page.state.readonly = true
    await tick()
    expect(controls.ownerProps.mock.lastCall![0].readonly).toBe(true)
  })
  it('allows implementation drafts in a pending stage while retaining explicit readonly', async () => {
    vi.mocked(getProject).mockResolvedValue({ id: 12, lifecycleStatus: 'ACTIVE' } as any)
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      projectId: 12, ownerContext: 'SOL', objectType: 'IMPLEMENTATION_SOLUTION', executionAllowed: false,
      ownerActions: ['QUERY'], allowedActions: [],
      businessView: { id: '31', componentKey: 'SOL_IMPLEMENTATION_SOLUTION', status: 'PUBLISHED' } as any
    }))
    const page = await setup({ pending: true })
    expect(controls.ownerProps.mock.lastCall![0].readonly).toBe(false)
    expect(controls.ownerProps.mock.lastCall![0].resolvedContext.taskId).toBeUndefined()
    page.state.readonly = true
    await tick()
    expect(controls.ownerProps.mock.lastCall![0].readonly).toBe(true)
  })
  it.each([
    { lifecycleStatus: 'CLOSED', viewStatus: 'PUBLISHED', strategy: 'REFERENCE_EXISTING' },
    { lifecycleStatus: 'ACTIVE', viewStatus: 'DISABLED', strategy: 'REFERENCE_EXISTING' },
    { lifecycleStatus: 'ACTIVE', viewStatus: 'PUBLISHED', strategy: 'READ_ONLY_AGGREGATE' }
  ])('does not open maintenance for an unavailable project/view or a frozen readonly binding: %j', async scenario => {
    vi.mocked(getProject).mockResolvedValue({ id: 12, lifecycleStatus: scenario.lifecycleStatus } as any)
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      projectId: 12, ownerContext: 'IMP', objectType: 'INSTALLATION', executionAllowed: false,
      instanceResolutionStrategy: scenario.strategy, ownerActions: ['QUERY'],
      businessView: { id: '31', componentKey: 'IMP_PROJECT_INSTALLATION', status: scenario.viewStatus } as any
    }))
    await setup({ completed: true })
    expect(controls.ownerProps.mock.lastCall![0].readonly).toBe(true)
  })
  it('blocks interaction while refreshing requirements without toggling editor permissions, then applies denial', async () => {
    const requirement = context('11', { ownerContext: 'SOL', objectType: 'REQUIREMENT_ANALYSIS' })
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(requirement)
    const page = await setup()
    expect(visit(page.root, node => node.props?.['aria-label'] === '来源交付件')).toBeUndefined()
    let resolve!: (value: TaskBusinessContext) => void
    vi.mocked(BusinessApi.getTaskBusinessContext).mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const refreshing = page.panel.value.refresh()
    await tick()
    const region = visit(page.root, node => node.props?.['aria-label'] === '任务业务办理')!
    expect(region.props?.inert).toBe(true)
    expect(controls.ownerProps.mock.lastCall![0].readonly).toBe(false)
    resolve({ ...requirement, executionAllowed: false })
    await refreshing; await tick()
    expect(region.props?.inert).toBe(false)
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: ['QUERY'] })
  })
  it('refreshes eligible business results through the command then reloads the task', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { allowedActions: ['REFRESH_RESULTS'] }))
    vi.mocked(BusinessApi.refreshTaskBusinessResults).mockResolvedValue({ completed: true, unknown: false })
    const page = await setup()
    await page.panel.value.refresh()
    expect(BusinessApi.refreshTaskBusinessResults).toHaveBeenCalledWith({
      taskId: '11', expectedTaskVersion: 4, expectedContractVersion: 3
    })
    expect(BusinessApi.getTaskBusinessContext).toHaveBeenCalledTimes(2)
    expect(page.changed).toHaveBeenCalled()
  })
  it('keeps read-only refresh as a query and respects rejected unsaved-content navigation', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { allowedActions: ['REFRESH_RESULTS'] }))
    const page = await setup({ readonly: true })
    await page.panel.value.refresh()
    expect(BusinessApi.refreshTaskBusinessResults).not.toHaveBeenCalled()
    page.state.readonly = false
    await tick()
    await click(page.root, 'Owner edit')
    controls.leave.mockResolvedValue(false)
    await page.panel.value.refresh()
    expect(BusinessApi.refreshTaskBusinessResults).not.toHaveBeenCalled()
  })
  it('does not report success or preserve completion facts after a failed refresh command', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { allowedActions: ['REFRESH_RESULTS'] }))
    vi.mocked(BusinessApi.refreshTaskBusinessResults).mockRejectedValue(new Error('version conflict'))
    const page = await setup()
    await page.panel.value.refresh()
    expect(page.changed).not.toHaveBeenCalled()
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    expect(textOf(page.root)).toContain('业务结果重新判定失败')
  })
  it('keeps the Owner workspace usable without the retired reopen confirmation step', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      allowedActions: ['CONFIRM_REOPEN_HANDLING']
    }))
    const page = await setup()
    expect(textOf(page.root)).not.toContain('确认本轮已核对')
    expect(controls.ownerProps.mock.lastCall![0].readonly).toBe(false)
    await click(page.root, 'Owner changed')
    await tick()
    expect(BusinessApi.getTaskBusinessContext).toHaveBeenCalledTimes(2)
    expect(page.facts).toHaveBeenLastCalledWith('fact-11')
  })
  it('refreshes task status after automatic completion even when Owner facts are unchanged', async () => {
    const execution: NonNullable<TaskBusinessContext['execution']> = {
      projectId: '2099999999999999999', projectVersion: 1, taskId: '11', taskVersion: 4,
      executionContractId: '21', contractVersion: 3, planVersionId: '61', executionId: '71',
      executionVersion: 2, roundNo: 1, stageExecutionId: '81', stageExecutionVersion: 1, writable: true
    }
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { execution }))
    const page = await setup()
    expect(page.changed).not.toHaveBeenCalled()
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      execution: { ...execution, taskVersion: 5, writable: false }, executionAllowed: false
    }))
    await page.panel.value.refresh()
    await tick()
    expect(page.changed).toHaveBeenCalledTimes(1)
    expect(page.facts).toHaveBeenLastCalledWith('fact-11')
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: ['QUERY'] })
    page.state.taskVersion = 5
    await tick()
    expect(page.changed).toHaveBeenCalledTimes(1)
  })
  it('renders the Owner page without a duplicate heading or automatic-association panel', async () => {
    const page = await setup()
    expect(textOf(page.root)).not.toContain('自动关联')
    expect(textOf(page.root)).not.toContain('record-41')
    expect(visit(page.root, (node) => node.type === 'h3')).toBeUndefined()
    expect(button(page.root, '刷新业务结果')).toBeUndefined()
    expect(button(page.root, '关联记录')).toBeUndefined()
    expect(button(page.root, '解除关联')).toBeUndefined()
    expect(BusinessApi.linkTaskBusinessObject).not.toHaveBeenCalled()
    expect(BusinessApi.unlinkTaskBusinessObject).not.toHaveBeenCalled()
    page.state.readonly = true
    await tick()
    expect(textOf(page.root)).toContain('owner:11/41')
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: ['QUERY'] })
  })
  it('keeps the task business view read-only when task execution is denied despite Owner actions', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { executionAllowed: false }))
    await setup()
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: ['QUERY'] })
    expect(BusinessApi.linkTaskBusinessObject).not.toHaveBeenCalled()
  })
  it('loads the frozen context without registration management or extra project query permissions', async () => {
    const page = await setup()
    expect(textOf(page.root)).toContain('owner:11/41')
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({
      registration: { id: '31' }, resolvedContext: { project: { id: '2099999999999999999' } },
      allowedActions: ['QUERY', 'CREATE', 'UPDATE'], readonly: false
    })
    expect(getProject).not.toHaveBeenCalled()
    expect(page.facts).toHaveBeenLastCalledWith('fact-11')
    expect(textOf(page.root)).toContain('survey.pdf')
    expect(textOf(page.root)).toContain('文件版本 2')
  })
  it('loads acceptance with only the authorized project ID and selected record commands', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      ownerContext: 'ACC', componentKey: 'ACC_ACCEPTANCE_REPORT', ownerActions: ['QUERY', 'MANAGE'],
      businessView: { id: '32', componentKey: 'ACC_ACCEPTANCE_REPORT' } as any,
      links: [{ ...record(), allowedActions: ['QUERY', 'MANAGE', 'LINK', 'UNLINK'] }]
    }))
    await setup()
    expect(getProject).not.toHaveBeenCalled()
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual(['QUERY', 'MANAGE'])
  })
  it('keeps blank Owner actions closed despite writable record facts', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { ownerActions: [] }))
    await setup()
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual([])
  })
  it('passes only context QUERY/CREATE without a selected record', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { links: [] }))
    const page = await setup()
    expect(textOf(page.root)).not.toContain('暂无自动关联')
    expect(textOf(page.root)).toContain('owner:11/undefined')
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual(['QUERY', 'CREATE'])
  })
  it('preserves authorized draft actions before asynchronous links arrive', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      links: [], ownerActions: ['QUERY', 'CREATE', 'CREATE_INITIAL_DRAFT', 'CREATE_DRAFT', 'PATCH_FORM', 'COMPLETE']
    }))
    const page = await setup()
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual([
      'QUERY', 'CREATE', 'CREATE_INITIAL_DRAFT', 'CREATE_DRAFT', 'PATCH_FORM', 'COMPLETE'
    ])
    page.state.readonly = true
    await tick()
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual(['QUERY'])
  })
  it('fails closed when a full Owner project cannot be queried, without fabricating a grant', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      projectId: 12, businessView: { id: '31', componentKey: 'PROJ_REQUIREMENT_ANALYSIS' } as any
    }))
    vi.mocked(getProject).mockRejectedValue(new Error('403'))
    const page = await setup()
    expect(getProject).toHaveBeenCalledWith(12)
    expect(controls.ownerProps).not.toHaveBeenCalled()
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    expect(textOf(page.root)).toContain('项目查询权限')
  })
  it('reuses the matching authorized project on entry but re-reads it on refresh', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      projectId: 12, businessView: { id: '31', componentKey: 'PROJ_REQUIREMENT_ANALYSIS' } as any
    }))
    const page = await setup({ initialProject: { id: 12, projectName: '已读取的项目' } })
    expect(getProject).not.toHaveBeenCalled()
    expect(controls.ownerProps.mock.lastCall![0].resolvedContext.project.projectName).toBe('已读取的项目')
    vi.mocked(getProject).mockRejectedValueOnce(new Error('403'))
    await page.panel.value.refresh()
    await tick()
    expect(getProject).toHaveBeenCalledWith(12)
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual(['QUERY'])
  })
  const requirementContext = (taskId = '11', patch: Partial<TaskBusinessContext> = {}) => context(taskId, {
    projectId: 12, ownerContext: 'SOL', objectType: 'REQUIREMENT_ANALYSIS',
    businessView: { id: '31', componentKey: 'PROJ_REQUIREMENT_ANALYSIS' } as any,
    execution: {
      projectId: 12, projectVersion: 1, taskId, taskVersion: 4,
      executionContractId: '21', contractVersion: 3, planVersionId: '61', executionId: '71',
      executionVersion: 2, roundNo: 1, stageExecutionId: '81', stageExecutionVersion: 1, writable: true
    },
    ...patch
  })
  it('prefetches the matching requirement workspace on entry and when selecting another task', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockImplementation(async id => requirementContext(String(id)))
    const page = await setup({ initialProject: { id: 12 } })
    expect(RequirementAnalysisApi.workspace).toHaveBeenCalledExactlyOnceWith(12, undefined, '11')
    expect(takeTaskViewPrefetch(requirementWorkspaceKey(12, undefined, '11'))).toBeInstanceOf(Promise)
    page.state.taskId = '12'
    await tick()
    expect(RequirementAnalysisApi.workspace).toHaveBeenCalledTimes(2)
    expect(RequirementAnalysisApi.workspace).toHaveBeenLastCalledWith(12, undefined, '12')
    expect(takeTaskViewPrefetch(requirementWorkspaceKey(12, undefined, '12'))).toBeInstanceOf(Promise)
    expect(getProject).not.toHaveBeenCalled()
  })
  it('refreshes saved Owner facts and denied permissions without another workspace prefetch', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(requirementContext())
    const page = await setup({ initialProject: { id: 12 } })
    await takeTaskViewPrefetch(requirementWorkspaceKey(12, undefined, '11'))
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(requirementContext('11', {
      factVersion: 'saved-fact', executionAllowed: false, ownerActions: ['QUERY']
    }))
    await click(page.root, 'Owner changed')
    expect(BusinessApi.getTaskBusinessContext).toHaveBeenCalledTimes(2)
    expect(getProject).toHaveBeenCalledExactlyOnceWith(12)
    expect(page.facts).toHaveBeenLastCalledWith('saved-fact')
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: ['QUERY'] })
    expect(RequirementAnalysisApi.workspace).toHaveBeenCalledTimes(1)
    expect(takeTaskViewPrefetch(requirementWorkspaceKey(12, undefined, '11'))).toBeUndefined()
  })
  it('keeps explicit refresh reading the Owner workspace without adding an unused prefetch', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(requirementContext())
    const page = await setup({ initialProject: { id: 12 } })
    await takeTaskViewPrefetch(requirementWorkspaceKey(12, undefined, '11'))
    controls.ownerRefresh.mockImplementation(() => RequirementAnalysisApi.workspace(12, undefined, '11'))
    await page.panel.value.refresh()
    await tick()
    expect(BusinessApi.getTaskBusinessContext).toHaveBeenCalledTimes(2)
    expect(getProject).toHaveBeenCalledExactlyOnceWith(12)
    expect(controls.ownerRefresh).toHaveBeenCalledTimes(1)
    expect(RequirementAnalysisApi.workspace).toHaveBeenCalledTimes(2)
    expect(takeTaskViewPrefetch(requirementWorkspaceKey(12, undefined, '11'))).toBeUndefined()
  })
  it('does not prefetch requirements for a different enclosing project', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(requirementContext())
    await setup({ initialProject: { id: 13 } })
    expect(getProject).toHaveBeenCalledExactlyOnceWith(12)
    expect(RequirementAnalysisApi.workspace).not.toHaveBeenCalled()
  })
  it('does not reuse another project or use the enclosing project to bypass denied context', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      projectId: 12, businessView: { id: '31', componentKey: 'PROJ_REQUIREMENT_ANALYSIS' } as any
    }))
    vi.mocked(getProject).mockRejectedValue(new Error('403'))
    await setup({ initialProject: { id: 13 } })
    expect(getProject).toHaveBeenCalledWith(12)
    expect(controls.ownerProps).not.toHaveBeenCalled()
    vi.mocked(BusinessApi.getTaskBusinessContext).mockRejectedValue(new Error('403'))
    await setup({ initialProject: { id: 12 } })
    expect(getProject).toHaveBeenCalledTimes(1)
    expect(controls.ownerProps).not.toHaveBeenCalled()
  })
  it('isolates old context responses even across A→B→A', async () => {
    const old = deferred<TaskBusinessContext>()
    vi.mocked(BusinessApi.getTaskBusinessContext).mockReturnValueOnce(old.promise)
    const page = await setup()
    page.state.taskId = '12'
    await tick()
    page.state.taskId = '11'
    await tick()
    old.resolve(context('11', { factVersion: 'obsolete', links: [record('99')] }))
    await tick()
    expect(textOf(page.root)).toContain('owner:11/41')
    expect(textOf(page.root)).not.toContain('record-99')
    expect(page.facts).toHaveBeenLastCalledWith('fact-11')
  })
  it('keeps dirty content when task-switch leave is cancelled', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    controls.leave.mockResolvedValue(false)
    page.state.taskId = '12'
    await tick()
    expect(textOf(page.root)).toContain('owner:11/41;dirty:true')
    expect(page.panel.value.isDirty()).toBe(true)
    expect(controls.discard).not.toHaveBeenCalled()
    expect(BusinessApi.getTaskBusinessContext).toHaveBeenCalledTimes(1)
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
  })
  it('confirms before loading a replacement and discards only after the Host accepts its target', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    const leave = deferred<boolean>(), next = deferred<TaskBusinessContext>()
    controls.leave.mockReturnValueOnce(leave.promise).mockResolvedValue(true)
    vi.mocked(BusinessApi.getTaskBusinessContext).mockReturnValueOnce(next.promise)
    page.state.taskId = '12'
    await tick()
    expect(BusinessApi.getTaskBusinessContext).toHaveBeenCalledTimes(1)
    expect(page.panel.value.isDirty()).toBe(true)
    leave.resolve(true)
    await tick()
    expect(controls.discard).not.toHaveBeenCalled()
    expect(page.panel.value.isDirty()).toBe(true)
    next.resolve(context('12'))
    await tick()
    expect(controls.discard).toHaveBeenCalledTimes(1)
    expect(textOf(page.root)).toContain('owner:12/41;dirty:false')
  })
  it('preserves dirty content when an automatic Owner record replacement is cancelled', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    controls.leave.mockResolvedValue(false)
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { links: [record('42', '52')] }))
    await click(page.root, 'Owner changed')
    expect(controls.leave).toHaveBeenCalledTimes(1)
    expect(controls.discard).not.toHaveBeenCalled()
    expect(textOf(page.root)).toContain('owner:11/41;dirty:true')
    expect(page.panel.value.isDirty()).toBe(true)
  })
  it('preserves dirty when discard is refused after task-switch confirmation', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    controls.discard.mockReturnValue(false)
    page.state.taskId = '12'
    await tick()
    expect(textOf(page.root)).toContain('owner:11/41;dirty:true')
    expect(page.panel.value.isDirty()).toBe(true)
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
  })
  it('clears expected fact version on refresh failure without unmounting dirty content', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    vi.mocked(BusinessApi.getTaskBusinessContext).mockRejectedValue(new Error('403'))
    await page.panel.value.refresh()
    await tick()
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    expect(textOf(page.root)).toContain('owner:11/41;dirty:true')
    expect(page.panel.value.isDirty()).toBe(true)
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual(['QUERY'])
  })
  it('does not discard dirty content for a superseded task-switch confirmation', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    const firstLeave = deferred<boolean>()
    controls.leave.mockReturnValueOnce(firstLeave.promise).mockResolvedValue(true)
    page.state.taskId = '12'
    await tick()
    page.state.taskId = '13'
    await tick()
    expect(controls.discard).not.toHaveBeenCalled()
    expect(page.panel.value.isDirty()).toBe(true)
    firstLeave.resolve(true)
    await tick()
    expect(BusinessApi.getTaskBusinessContext).not.toHaveBeenCalledWith('12')
    expect(textOf(page.root)).toContain('owner:13/41')
    expect(controls.discard).toHaveBeenCalledTimes(1)
    expect(page.facts).toHaveBeenLastCalledWith('fact-13')
  })
  it('retains the original dirty target across successive automatic record replacements', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    const leave = deferred<boolean>()
    controls.leave.mockReturnValue(leave.promise)
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { links: [record('42', '52')] }))
    await click(page.root, 'Owner changed')
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { links: [record('43', '53')] }))
    await click(page.root, 'Owner changed')
    leave.resolve(false)
    await tick()
    expect(textOf(page.root)).toContain('owner:11/41;dirty:true')
    expect(page.panel.value.isDirty()).toBe(true)
    expect(controls.discard).not.toHaveBeenCalled()
  })
  it('does not unmount dirty history when refreshed registration becomes unavailable', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { businessView: undefined }))
    await page.panel.value.refresh()
    await tick()
    expect(textOf(page.root)).toContain('owner:11/41;dirty:true')
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    expect(controls.discard).not.toHaveBeenCalled()
  })
  it('ignores an old Owner changed event while a dirty task switch is blocked', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    controls.leave.mockResolvedValue(false)
    page.state.taskId = '12'
    await tick()
    await click(page.root, 'Owner changed')
    expect(BusinessApi.getTaskBusinessContext).toHaveBeenCalledTimes(1)
    expect(page.panel.value.isDirty()).toBe(true)
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
  })
  it('does not publish a fact version with recoverable errors', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { recoverableError: 'OWNER_UNAVAILABLE' }))
    const page = await setup()
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    expect(button(page.root, '关联记录')).toBeUndefined()
  })
})

describe('completed assignment member maintenance', () => {
  const assignment = () => context('11', {
    ownerContext: 'PROJ', objectType: 'PROJECT_ASSIGNMENT',
    componentKey: 'PROJ_PROJECT_ASSIGNMENT', executionAllowed: false,
    ownerActions: ['QUERY'], allowedActions: ['REFRESH_RESULTS'],
    businessView: { id: '31', componentKey: 'PROJ_PROJECT_ASSIGNMENT' } as any
  })
  it('maintains members without rerunning completed task commands and respects leave guard', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(assignment())
    const page = await setup({ completed: true })
    expect(textOf(page.root)).toContain('member-maintenance;readonly:false')
    await page.panel.value.refresh()
    expect(BusinessApi.refreshTaskBusinessResults).not.toHaveBeenCalled()
    controls.leave.mockResolvedValue(false)
    expect(await page.panel.value.requestLeave()).toBe(false)
    page.state.pending = true
    await tick()
    expect(textOf(page.root)).toContain('member-maintenance;readonly:true')
  })
  it.each([{ readonly: true }, { recoverableError: 'unavailable' }])('retains restrictions %j', async (restriction) => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue({ ...assignment(), ...restriction })
    const page = await setup({ completed: true, readonly: restriction.readonly })
    expect(textOf(page.root)).toContain('member-maintenance;readonly:true')
  })
  it('keeps other completed business readonly', async () => {
    const page = await setup({ completed: true })
    expect(textOf(page.root)).not.toContain('member-maintenance')
    expect(textOf(page.root)).toContain('readonly:true')
  })
  const cutover = () => context('11', {
    ownerContext: 'CUT', objectType: 'CUTOVER_APPROVAL', executionAllowed: false,
    ownerActions: ['QUERY'], allowedActions: [],
    businessView: { id: '31', componentKey: 'CUT_CUTOVER_APPROVAL_WORKBENCH' } as any
  })
  it('continues only the linked cutover closure after the approval task completes', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(cutover())
    const page = await setup({ completed: true })
    expect(textOf(page.root)).toContain('cutover-maintenance:41;closure-only:true;readonly:false')
    await page.panel.value.refresh()
    expect(BusinessApi.refreshTaskBusinessResults).not.toHaveBeenCalled()
    controls.leave.mockResolvedValue(false)
    expect(await page.panel.value.requestLeave()).toBe(false)
  })
  it.each([{ readonly: true }, { recoverableError: 'unavailable' }])('retains cutover restrictions %j', async restriction => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue({ ...cutover(), ...restriction })
    const page = await setup({ completed: true, readonly: restriction.readonly })
    expect(textOf(page.root)).toContain('closure-only:true;readonly:true')
  })
  it('does not create cutover maintenance without an inspected linked object', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue({ ...cutover(), links: [] })
    const page = await setup({ completed: true })
    expect(textOf(page.root)).not.toContain('cutover-maintenance')
    expect(textOf(page.root)).toContain('readonly:true')
  })
})
