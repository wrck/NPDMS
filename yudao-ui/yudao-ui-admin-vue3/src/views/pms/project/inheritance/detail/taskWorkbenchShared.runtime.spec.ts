import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Drawer from '../../project-master-detail/components/ProjectNodeWorkbenchDrawer.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const leave = vi.hoisted(() => vi.fn())
const observed = vi.hoisted(() => ({
  selection: undefined as unknown,
  projectId: undefined as unknown,
  changed: undefined as undefined | (() => void),
  close: undefined as undefined | ((done: () => void) => Promise<void>)
}))
vi.mock('../../project-master-detail/components/ProjectFlowPanel.vue', () => ({
  default: defineComponent({
    props: ['projectId', 'project', 'selection', 'showResponsibilities'],
    emits: ['changed'],
    setup(props, { expose, emit }) {
      observed.projectId = props.projectId
      observed.selection = props.selection
      observed.changed = () => emit('changed')
      expose({ requestLeave: leave })
      return () => h('section', '交付流程动态任务办理')
    }
  })
}))
const drawerShell = defineComponent({
  props: ['modelValue', 'beforeClose'],
  setup(props, { slots }) {
    observed.close = props.beforeClose as (done: () => void) => Promise<void>
    return () => (props.modelValue ? h('div', slots.default?.()) : null)
  }
})
const apps: { unmount: () => void }[] = []
beforeEach(() => {
  vi.clearAllMocks()
  observed.selection = undefined
  observed.changed = undefined
  leave.mockResolvedValue(true)
})
afterEach(() => apps.splice(0).forEach((app) => app.unmount()))

it('renders the canonical delivery-flow task view with the same project and node context', async () => {
  const changed = vi.fn()
  const view = mount(
    Drawer,
    { modelValue: true, project: { id: 9 }, selection: { kind: 'task', taskId: 10, stageCode: 'PREPARE' }, onChanged: changed },
    { ElDrawer: drawerShell }
  )
  apps.push(view.app)
  await nextTick()
  expect(textOf(view.root)).toContain('交付流程动态任务办理')
  expect(observed.projectId).toBe(9)
  expect(observed.selection).toEqual({ kind: 'task', stageCode: 'PREPARE', taskId: 10 })
  observed.changed?.()
  expect(changed).toHaveBeenCalledTimes(1)
})

it('uses the same Owner dirty-state refusal when closing the task entrance', async () => {
  const view = mount(
    Drawer,
    { modelValue: true, project: { id: 9 }, selection: { kind: 'task', taskId: 10, stageCode: 'PREPARE' } },
    { ElDrawer: drawerShell }
  )
  apps.push(view.app)
  await nextTick()
  const done = vi.fn()
  leave.mockResolvedValue(false)
  await observed.close?.(done)
  expect(done).not.toHaveBeenCalled()
  leave.mockResolvedValue(true)
  await observed.close?.(done)
  expect(done).toHaveBeenCalledTimes(1)
})

it('does not substitute a stage identity when a task context is missing', async () => {
  const view = mount(
    Drawer,
    { modelValue: true, project: { id: 9 }, selection: { kind: 'task', stageCode: 'PREPARE' } },
    { ElDrawer: drawerShell }
  )
  apps.push(view.app)
  await nextTick()
  expect(observed.selection).toBeUndefined()
  expect(textOf(view.root)).not.toContain('交付流程动态任务办理')
})

it('opens the same delivery-flow view with an explicit stage selection and no borrowed task', async () => {
  const view = mount(
    Drawer,
    { modelValue: true, project: { id: 9 }, selection: { kind: 'stage', stageCode: 'PREP_WORK' } },
    { ElDrawer: drawerShell }
  )
  apps.push(view.app)
  await nextTick()
  expect(observed.selection).toEqual({ kind: 'stage', stageCode: 'PREP_WORK' })
  expect(observed.projectId).toBe(9)
  const done = vi.fn()
  leave.mockResolvedValue(false)
  await observed.close?.(done)
  expect(done).not.toHaveBeenCalled()
  leave.mockResolvedValue(true)
  await observed.close?.(done)
  expect(done).toHaveBeenCalledOnce()
})

it('does not mount a stage without its project or stage code', async () => {
  for (const props of [
    { project: {}, selection: { kind: 'stage', stageCode: 'PREP_WORK' } },
    { project: { id: 9 }, selection: { kind: 'stage', stageCode: '' } }
  ]) {
    const view = mount(Drawer, { modelValue: true, ...props }, { ElDrawer: drawerShell })
    apps.push(view.app)
    await nextTick()
    expect(observed.selection).toBeUndefined()
  }
})
