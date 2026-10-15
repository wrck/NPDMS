import { defineComponent, h, nextTick, reactive } from 'vue'
import { expect, it, vi } from 'vitest'
import * as Projects from '@/api/pms/project/projects'
import ProjectEndUsersWorkbench from './ProjectEndUsersWorkbench.vue'
import { mount, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/projects', () => ({ getProject: vi.fn() }))
vi.mock('@/views/pms/project/inheritance/members/ProjectMembersPanel.vue', () => ({
  default: defineComponent({ props: ['project', 'readonly', 'initialRole'], emits: ['updated'],
    setup: (props, { emit }) => () => h('member-panel', { ...props, onUpdated: () => emit('updated') }) })
}))
const findPanel = (node: TestNode): TestNode | undefined => node.type === 'member-panel' ? node
  : node.children.map(findPanel).find(Boolean)
const flush = async () => { await Promise.resolve(); await nextTick(); await Promise.resolve(); await nextTick() }

it('discards the previous project response, forwards readonly and refreshes the saved project version', async () => {
  let resolveOld!: (value: Projects.ProjectMasterVO) => void
  vi.mocked(Projects.getProject).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    .mockResolvedValueOnce({ id: 2, version: 4 }).mockResolvedValueOnce({ id: 2, version: 5 })
  const props = reactive({ projectId: 1, readonly: true })
  const changed = vi.fn()
  const host = defineComponent({ setup: () => () => h(ProjectEndUsersWorkbench, { ...props, onChanged: changed }) })
  const { root, app } = mount(host)
  props.projectId = 2
  await flush()
  resolveOld({ id: 1, version: 1 })
  await flush()
  let panel = findPanel(root)!
  expect(panel.props?.project).toEqual({ id: 2, version: 4 })
  expect(panel.props?.readonly).toBe(true)
  expect(panel.props?.initialRole).toBe('END_USER')
  props.readonly = false
  await nextTick()
  panel = findPanel(root)!
  expect(panel.props?.readonly).toBe(false)
  ;(panel.props?.onUpdated as () => void)()
  await flush()
  expect(findPanel(root)?.props?.project).toEqual({ id: 2, version: 5 })
  expect(changed).toHaveBeenCalledOnce()
  app.unmount()
})
