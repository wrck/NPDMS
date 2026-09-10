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
const select = (root: TestNode) => visit(root, (node) => node.props?.placeholder === '选择本项目已有业务记录')!
const choose = async (root: TestNode, objectId = '42') => {
  (select(root).props!['onUpdate:modelValue'] as Function)(objectId)
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
    await setup()
    expect(controls.ownerProps.mock.lastCall![0].allowedActions).toEqual(['QUERY', 'CREATE'])
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
  it('reuses the LINK retry key for identical payloads, but changes it for a new task version', async () => {
    const page = await setup()
    vi.mocked(BusinessApi.linkTaskBusinessObject).mockRejectedValue(new Error('lost response'))
    await choose(page.root)
    await click(page.root, '关联记录')
    await click(page.root, '关联记录')
    const calls = vi.mocked(BusinessApi.linkTaskBusinessObject).mock.calls
    expect(calls[0]).toEqual(calls[1])
    expect(calls[0].slice(0, 4)).toEqual(['11', '42', 4, 3])
    expect(calls[0][4]).toBeTruthy()
    page.state.taskVersion = 5
    await tick()
    await choose(page.root)
    await click(page.root, '关联记录')
    expect(calls[2][4]).not.toBe(calls[0][4])
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
  })
  it('rotates the LINK key after confirmed success', async () => {
    const page = await setup()
    await choose(page.root)
    await click(page.root, '关联记录')
    await choose(page.root)
    await click(page.root, '关联记录')
    const calls = vi.mocked(BusinessApi.linkTaskBusinessObject).mock.calls
    expect(calls[1][4]).not.toBe(calls[0][4])
    expect(page.changed).toHaveBeenCalledTimes(2)
  })
  it.each(['LINK', 'UNLINK'])('revalidates a captured %s handler after action revocation', async (action) => {
    const page = await setup()
    await choose(page.root)
    const handler = button(page.root, action === 'LINK' ? '关联记录' : '解除关联')!.props!.onClick as Function
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { allowedActions: [] }))
    await page.panel.value.refresh()
    await handler()
    expect(BusinessApi.linkTaskBusinessObject).not.toHaveBeenCalled()
    expect(BusinessApi.unlinkTaskBusinessObject).not.toHaveBeenCalled()
    expect(controls.confirm).not.toHaveBeenCalled()
  })
  it('treats unlink cancellation as a no-op', async () => {
    const page = await setup()
    controls.confirm.mockRejectedValue('cancel')
    await click(page.root, '解除关联')
    expect(BusinessApi.unlinkTaskBusinessObject).not.toHaveBeenCalled()
    expect(page.changed).not.toHaveBeenCalled()
  })
  it.each(['task', 'permission', 'readonly', 'version', 'record'])('aborts unlink when %s changes during confirmation', async (change) => {
    const page = await setup()
    const confirmation = deferred<void>()
    controls.confirm.mockReturnValue(confirmation.promise)
    const pending = (button(page.root, '解除关联')!.props!.onClick as Function)()
    if (change === 'task') page.state.taskId = '12'
    if (change === 'readonly') page.state.readonly = true
    if (change === 'version') page.state.taskVersion = 5
    if (change === 'permission' || change === 'record') {
      vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', change === 'permission'
        ? { allowedActions: ['LINK'] } : { links: [record('43', '53')] }))
      await page.panel.value.refresh()
    }
    await tick()
    confirmation.resolve()
    await pending
    expect(BusinessApi.unlinkTaskBusinessObject).not.toHaveBeenCalled()
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
  it('isolates candidate responses and loading across task changes', async () => {
    const page = await setup()
    const old = deferred<TaskBusinessLink[]>(), fresh = deferred<TaskBusinessLink[]>()
    vi.mocked(BusinessApi.getTaskBusinessCandidates).mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise)
    const first = (select(page.root).props!['onVisibleChange'] as Function)(true)
    page.state.taskId = '12'
    await tick()
    const second = (select(page.root).props!['onVisibleChange'] as Function)(true)
    old.resolve([record('99')])
    await first
    await tick()
    expect(select(page.root).props!.loading).toBe(true)
    expect(textOf(page.root)).not.toContain('record-99')
    fresh.resolve([record('44')])
    await second
    await tick()
    expect(select(page.root).props!.loading).toBe(false)
  })
  it('does not let a late LINK response reload or emit changes for a replacement task', async () => {
    const page = await setup()
    const response = deferred<any>()
    vi.mocked(BusinessApi.linkTaskBusinessObject).mockReturnValue(response.promise)
    await choose(page.root)
    const pending = (button(page.root, '关联记录')!.props!.onClick as Function)()
    page.state.taskId = '12'
    await tick()
    response.resolve({})
    await pending
    await tick()
    expect(page.changed).not.toHaveBeenCalled()
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    await page.panel.value.refresh()
    await tick()
    expect(textOf(page.root)).toContain('owner:12/41')
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
  it('delegates record selection to the Host without duplicate confirmation and preserves dirty on cancellation', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { links: [record(), record('42', '52')] }))
    const page = await setup()
    await click(page.root, 'Owner edit')
    controls.leave.mockResolvedValue(false)
    await click(page.root, 'record-42')
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
  it('keeps terminal history visible and rechecks readonly in captured handlers', async () => {
    const page = await setup()
    await choose(page.root)
    const link = button(page.root, '关联记录')!.props!.onClick as Function
    const unlink = button(page.root, '解除关联')!.props!.onClick as Function
    await click(page.root, 'Owner edit')
    page.state.readonly = true
    await tick()
    await link()
    await unlink()
    expect(BusinessApi.linkTaskBusinessObject).not.toHaveBeenCalled()
    expect(BusinessApi.unlinkTaskBusinessObject).not.toHaveBeenCalled()
    expect(textOf(page.root)).toContain('record-41')
    expect(textOf(page.root)).toContain('survey.pdf')
    expect(page.panel.value.isDirty()).toBe(true)
    expect(controls.ownerProps.mock.lastCall![0]).toMatchObject({ readonly: true, allowedActions: [] })
  })
  it('ignores an older candidate request even for the same task', async () => {
    const page = await setup()
    const old = deferred<TaskBusinessLink[]>(), fresh = deferred<TaskBusinessLink[]>()
    vi.mocked(BusinessApi.getTaskBusinessCandidates).mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise)
    const first = (select(page.root).props!['onVisibleChange'] as Function)(true)
    const second = (select(page.root).props!['onVisibleChange'] as Function)(true)
    fresh.resolve([record('44')])
    await second
    old.reject(new Error('late failure'))
    await first
    await tick()
    expect(controls.warning).not.toHaveBeenCalled()
    const option = visit(page.root, (node) => node.props?.label === 'record-44')
    expect(option).toBeTruthy()
  })
  it('ignores a late UNLINK result after a task switch', async () => {
    const page = await setup()
    const response = deferred<any>()
    vi.mocked(BusinessApi.unlinkTaskBusinessObject).mockReturnValue(response.promise)
    const pending = (button(page.root, '解除关联')!.props!.onClick as Function)()
    await tick()
    expect(BusinessApi.unlinkTaskBusinessObject).toHaveBeenCalledWith('11', '51', 4, 3, expect.any(String))
    page.state.taskId = '12'
    await tick()
    response.resolve({})
    await pending
    expect(page.changed).not.toHaveBeenCalled()
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    expect(BusinessApi.getTaskBusinessContext).toHaveBeenCalledTimes(1)
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
  it('retains the original dirty target across multiple cancelled record switches', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', {
      links: [record(), record('42', '52'), record('43', '53')]
    }))
    const page = await setup()
    await click(page.root, 'Owner edit')
    const leave = deferred<boolean>()
    controls.leave.mockReturnValue(leave.promise)
    await click(page.root, 'record-42')
    await click(page.root, 'record-43')
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
  it('does not restore stale completion facts when dirty is discarded after a failed command', async () => {
    vi.mocked(BusinessApi.getTaskBusinessContext).mockResolvedValue(context('11', { links: [record(), record('42', '52')] }))
    const page = await setup()
    await click(page.root, 'Owner edit')
    vi.mocked(BusinessApi.linkTaskBusinessObject).mockRejectedValue(new Error('unknown outcome'))
    await choose(page.root)
    await click(page.root, '关联记录')
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
    await click(page.root, 'record-42')
    expect(page.panel.value.isDirty()).toBe(false)
    expect(page.facts).toHaveBeenLastCalledWith(undefined)
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
