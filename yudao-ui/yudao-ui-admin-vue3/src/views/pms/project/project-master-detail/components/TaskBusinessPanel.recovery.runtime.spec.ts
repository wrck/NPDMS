import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TaskBusinessPanel from '@/views/pms/project/project-master-detail/components/TaskBusinessPanel.vue'
import * as BusinessApi from '@/api/pms/project/task-business'
import { getProject } from '@/api/pms/project/projects'
import * as ReportApi from '@/api/pms/acceptance/acceptance-report'
import { inspectOperationCapabilities } from '@/api/pms/project/execution-operations'
import { getBusinessView } from '@/api/pms/platform/business-view'
import type { TaskBusinessContext, TaskBusinessLink } from '@/api/pms/project/task-business'
import {
  mount, passthrough, textOf, type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const controls = vi.hoisted(() => ({
  confirm: vi.fn(), leave: vi.fn(), discard: vi.fn(), ownerProps: vi.fn(), warning: vi.fn(),
  save: vi.fn(), mounted: vi.fn()
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => controls }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@/api/pms/project/task-business', () => ({
  getTaskBusinessContext: vi.fn(), getTaskBusinessCandidates: vi.fn(),
  linkTaskBusinessObject: vi.fn(), unlinkTaskBusinessObject: vi.fn(), refreshTaskBusinessResults: vi.fn()
}))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
vi.mock('@/api/pms/acceptance/acceptance-report', () => ({ getActivities: vi.fn() }))
vi.mock('@/api/pms/engineering/requirement-analysis/entity', () => ({ getWorkspace: vi.fn() }))
vi.mock('@/api/pms/project/execution-operations', () => ({ inspectOperationCapabilities: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/config/axios/service', () => ({ service: {} }))
vi.mock('@/api/pms/platform/business-view', () => ({ getBusinessView: vi.fn() }))
// Keep the real Host and its two-phase target watcher. Only its Owner child/registry
// are substituted, so no private Panel functions or copied implementation are tested.
vi.mock('@/components/BusinessView/registry', async () => {
  const { defineComponent, h, ref } = await import('vue')
  const FailedSave = defineComponent({ emits: ['save'], setup(_, { emit }) { return () => h('button', { onClick: () => emit('save') }, 'Fail save') } })
  const Owner = defineComponent({
    props: { registration: Object, resolvedContext: { type: Object, required: true }, allowedActions: Array, readonly: Boolean },
    setup(props, { emit, expose }) {
      controls.mounted()
      const dirty = ref(false)
      const input = ref('')
      expose({
        isDirty: () => dirty.value,
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
          h('input', {
            value: input.value,
            onInput: (event: { target: { value: string } }) => {
              input.value = event.target.value
              dirty.value = true
              emit('dirty-change', true)
            }
          }),
          h('button', { onClick: () => emit('changed') }, 'Owner changed'),
          h(FailedSave, { onSave: async () => {
            await controls.save(props.resolvedContext.project.version, input.value)
            dirty.value = false
            emit('dirty-change', false)
            emit('changed')
          } })
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
  const state = reactive({ taskId: '11', taskVersion: 4, readonly: false, ...initial })
  const panel = ref<any>()
  const facts = vi.fn(), changed = vi.fn(), dirtyChanged = vi.fn()
  const mounted = mount(defineComponent({ setup: () => () => h(TaskBusinessPanel, {
    ...state, ref: panel, onFactVersion: facts, onChanged: changed, onDirtyChange: dirtyChanged
  }) }), {}, { ElSelect: passthrough, ElOption: passthrough, ElTable: passthrough, ElTableColumn: passthrough })
  apps.push(mounted.app)
  await tick()
  return { ...mounted, state, panel, facts, changed, dirtyChanged }
}
beforeEach(() => {
  vi.resetAllMocks()
  controls.confirm.mockResolvedValue(undefined)
  controls.leave.mockResolvedValue(true)
  controls.discard.mockReturnValue(true)
  controls.save.mockRejectedValue(new Error('Project版本冲突，请重新加载后重试'))
  vi.mocked(inspectOperationCapabilities).mockImplementation(async (query) => ({
    node: { projectId: query.projectId, id: query.nodeId, kind: query.nodeKind, status: 'IN_PROGRESS' },
    reason: 'LEGACY_BINDING', actions: []
  } as any))
  vi.mocked(BusinessApi.getTaskBusinessContext).mockImplementation(async (id) => context(id))
  vi.mocked(BusinessApi.getTaskBusinessCandidates).mockResolvedValue([record('42')])
  vi.mocked(BusinessApi.linkTaskBusinessObject).mockResolvedValue({} as any)
  vi.mocked(BusinessApi.unlinkTaskBusinessObject).mockResolvedValue({} as any)
  vi.mocked(getProject).mockResolvedValue({ id: 12 } as any)
})
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  expect(getBusinessView).not.toHaveBeenCalled()
})


describe('shared workspace version and failed-save recovery', () => {
  it('uses an unlinked final report only when both the server candidate list and native project/type facts authorize it', async () => {
    const final = { ...record('42'), allowedActions: ['QUERY', 'UPDATE', 'PUBLISH', 'FILE_WRITE'] }
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      projectId: '12', ownerContext: 'ACC', objectType: 'ACCEPTANCE', componentKey: 'ACC_ACCEPTANCE_REPORT',
      links: [], ownerActions: ['QUERY', 'MANAGE'],
      businessView: { id: '31', componentKey: 'ACC_ACCEPTANCE_REPORT' } as any
    }))
    vi.mocked(BusinessApi.getTaskBusinessCandidates).mockResolvedValue([record('41'), final, record('43')])
    vi.mocked(ReportApi.getActivities).mockResolvedValue([
      { id: '41', projectId: '12', acceptanceType: 'PRELIMINARY' },
      { id: '42', projectId: '12', acceptanceType: 'FINAL' },
      { id: '43', projectId: '99', acceptanceType: 'FINAL' }
    ] as any)
    const page = await setup({ taskCode: 'ACCEPT_FINAL' })
    expect(controls.ownerProps.mock.lastCall?.[0].resolvedContext.businessObjectId).toBe('42')
    expect(controls.ownerProps.mock.lastCall?.[0].allowedActions).toContain('PUBLISH')
    expect(BusinessApi.linkTaskBusinessObject).not.toHaveBeenCalled()
    page.state.readonly = true; await tick()
    expect(controls.ownerProps.mock.lastCall?.[0].allowedActions).toEqual(['QUERY'])
  })
  it('propagates refreshed project version into the same active task workspace', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      projectId: '12', businessView: { id: '31', componentKey: 'PLN_STAGE_PLAN_APPROVAL' } as any
    }))
    const page = await setup({ initialProject: { id: 12, version: 14 } })
    await click(page.root, 'Owner edit')
    expect(controls.ownerProps.mock.lastCall?.[0].resolvedContext.project.version).toBe(14)
    page.state.initialProject = { id: 12, version: 15 }
    await tick()
    expect(controls.ownerProps.mock.lastCall?.[0].resolvedContext.project.version).toBe(15)
    expect(textOf(page.root)).toContain('dirty:true')
    expect(controls.mounted).toHaveBeenCalledTimes(1)
    expect(controls.discard).not.toHaveBeenCalled()
  })
  it('retains the editable Owner after a recoverable save version conflict', async () => {
    const page = await setup()
    await click(page.root, 'Owner edit')
    expect(textOf(page.root)).toContain('dirty:true')
    await click(page.root, 'Fail save')
    expect(textOf(page.root)).not.toContain('业务组件暂不可用')
    expect(textOf(page.root)).toContain('dirty:true')
    expect(page.panel.value.isDirty()).toBe(true)
    expect(controls.mounted).toHaveBeenCalledTimes(1)
  })
  it('preserves entered text after rejection and submits the refreshed version only on explicit retry', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { projectId: '12', businessView: { id: '31', componentKey: 'PLN_STAGE_PLAN_APPROVAL' } as any }))
    const page = await setup({ initialProject: { id: 12, version: 14 } })
    const input = visit(page.root, (node) => node.type === 'input')!
    ;(input.props!.onInput as Function)({ target: { value: '保留这段未保存说明' } })
    await tick()
    await click(page.root, 'Fail save')
    expect(textOf(page.root)).toContain('未保存内容已保留')
    expect(input.props!.value).toBe('保留这段未保存说明')
    expect(controls.save).toHaveBeenLastCalledWith(14, '保留这段未保存说明')
    page.state.initialProject = { id: 12, version: 15 }
    await tick()
    expect(controls.save).toHaveBeenCalledTimes(1)
    expect(visit(page.root, (node) => node.type === 'input')).toBe(input)
    expect(input.props!.value).toBe('保留这段未保存说明')
    controls.save.mockResolvedValue(undefined)
    await click(page.root, 'Fail save')
    expect(controls.save).toHaveBeenLastCalledWith(15, '保留这段未保存说明')
    expect(textOf(page.root)).not.toContain('未保存内容已保留')
    expect(page.panel.value.isDirty()).toBe(false)
  })
  it('ignores older versions and other projects without replacing the active Owner', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { projectId: '12', businessView: { id: '31', componentKey: 'PLN_STAGE_PLAN_APPROVAL' } as any }))
    const page = await setup({ initialProject: { id: 12, version: 15 } })
    page.state.initialProject = { id: 12, version: 14 }
    await tick()
    expect(controls.ownerProps.mock.lastCall?.[0].resolvedContext.project.version).toBe(15)
    page.state.initialProject = { id: 13, version: 20 }
    await tick()
    expect(controls.ownerProps.mock.lastCall?.[0].resolvedContext.project.id).toBe(12)
    expect(controls.ownerProps.mock.lastCall?.[0].resolvedContext.project.version).toBe(15)
    expect(controls.mounted).toHaveBeenCalledTimes(1)
  })
})
