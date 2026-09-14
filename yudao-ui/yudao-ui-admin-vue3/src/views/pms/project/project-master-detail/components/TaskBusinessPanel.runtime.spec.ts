import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TaskBusinessPanel from './TaskBusinessPanel.vue'
import * as BusinessApi from '@/api/pms/project/task-business'
import { getProject } from '@/api/pms/project/projects'
import { getBusinessView } from '@/api/pms/platform/business-view'
import type { TaskBusinessContext, TaskBusinessLink } from '@/api/pms/project/task-business'
import {
  mount, passthrough, textOf, type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const controls = vi.hoisted(() => ({
  confirm: vi.fn(), leave: vi.fn(), discard: vi.fn(), ownerProps: vi.fn(), warning: vi.fn()
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => controls }))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn() }))
vi.mock('@/api/pms/project/task-business', () => ({
  getTaskBusinessContext: vi.fn(), getTaskBusinessCandidates: vi.fn(),
  linkTaskBusinessObject: vi.fn(), unlinkTaskBusinessObject: vi.fn()
}))
vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
vi.mock('@/api/pms/platform/business-view', () => ({ getBusinessView: vi.fn() }))
// Keep the real Host and its two-phase target watcher. Only its Owner child/registry
// are substituted, so no private Panel functions or copied implementation are tested.
vi.mock('@/components/BusinessView/registry', async () => {
  const { defineComponent, h, ref } = await import('vue')
  const Owner = defineComponent({
    props: ['registration', 'resolvedContext', 'allowedActions', 'readonly'],
    setup(props, { emit, expose }) {
      const dirty = ref(false)
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
  const state = reactive({ taskId: '11', taskVersion: 4, readonly: false, ...initial })
  const panel = ref<any>()
  const facts = vi.fn(), changed = vi.fn(), dirtyChanged = vi.fn()
  const mounted = mount(defineComponent({ setup: () => () => h(TaskBusinessPanel, {
    ...state, ref: panel, onFactVersion: facts, onChanged: changed, onDirtyChange: dirtyChanged
  }) }), {}, { ElSelect: passthrough, ElOption: passthrough })
  apps.push(mounted.app)
  await tick()
  return { ...mounted, state, panel, facts, changed, dirtyChanged }
}
beforeEach(() => {
  vi.resetAllMocks()
  controls.confirm.mockResolvedValue(undefined)
  controls.leave.mockResolvedValue(true)
  controls.discard.mockReturnValue(true)
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

describe('PM-11 / PM-03 TaskBusinessPanel runtime', () => {
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
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: [] })
  })
  it('keeps the task business view read-only when task execution is denied despite Owner actions', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { executionAllowed: false }))
    await setup()
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: [] })
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
  it('preserves named Owner creation commands without granting unselected record writes', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      links: [], ownerActions: ['QUERY', 'CREATE', 'CREATE_INITIAL_DRAFT', 'CREATE_DRAFT', 'PATCH_FORM', 'COMPLETE']
    }))
    const page = await setup()
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual([
      'QUERY', 'CREATE', 'CREATE_INITIAL_DRAFT', 'CREATE_DRAFT'
    ])
    page.state.readonly = true
    await tick()
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual([])
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
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual([])
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
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual([])
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
