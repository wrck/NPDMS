import { afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, provide, ref } from 'vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import ProjectApprovalDrawer from './ProjectApprovalDrawer.vue'
import { projectApprovalDetailKey, useProjectApprovalDetail } from './projectApprovalDetail'

const navigation = vi.hoisted(() => ({ push: vi.fn(), mounts: 0 }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: navigation.push }) }))
vi.mock('@/views/bpm/processInstance/detail/index.vue', () => ({ default: defineComponent({
  props: ['id'],
  setup(props) {
    navigation.mounts++
    return () => h('div', `审批实例 ${props.id}`)
  }
}) }))

const apps: { unmount: () => void }[] = []
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.clearAllMocks(); navigation.mounts = 0 })
const flush = async () => { for (let i = 0; i < 30; i++) { await Promise.resolve(); await nextTick() } }
const Entry = defineComponent({ setup(_, { expose }) {
  const open = useProjectApprovalDetail()
  expose({ open })
  return () => h('button', '查看审批记录')
} })
const render = (inProject: boolean) => {
  const drawer = ref<any>(), entry = ref<any>()
  const view = mount(defineComponent({ setup() {
    if (inProject) provide(projectApprovalDetailKey, id => drawer.value.open(id))
    return () => [h(Entry, { ref: entry }), h(ProjectApprovalDrawer, { ref: drawer })]
  } }))
  apps.push(view.app)
  return { ...view, drawer, entry }
}

it('opens the requested instance inside the project without navigating away', async () => {
  const view = render(true)
  await view.entry.value.open('instance-current-round'); await flush()
  expect(textOf(view.root)).toContain('审批实例 instance-current-round')
  expect(navigation.push).not.toHaveBeenCalled()
})

it('reloads records after closing and remounts when choosing a different instance', async () => {
  const view = render(true)
  await view.entry.value.open('instance-1'); await flush()
  view.drawer.value.$.setupState.visible = false; await flush()
  expect(textOf(view.root)).not.toContain('审批实例 instance-1')
  await view.entry.value.open('instance-1'); await flush()
  expect(navigation.mounts).toBe(2)
  await view.entry.value.open('instance-2'); await flush()
  expect(textOf(view.root)).toContain('审批实例 instance-2')
  expect(textOf(view.root)).not.toContain('审批实例 instance-1')
  expect(navigation.mounts).toBe(3)
})

it('retains the existing detail route for business components outside project detail', async () => {
  const view = render(false)
  await view.entry.value.open('instance-standalone')
  expect(navigation.push).toHaveBeenCalledWith({ name: 'BpmProcessInstanceDetail', query: { id: 'instance-standalone' } })
  expect(navigation.mounts).toBe(0)
})
