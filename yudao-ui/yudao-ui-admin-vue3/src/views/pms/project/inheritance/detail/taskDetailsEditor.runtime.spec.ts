import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import DetailsEditor from '../../project-master-detail/components/ProjectTaskDetailsEditor.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ updateTask: vi.fn(), updateTaskProgress: vi.fn() }))
const before = vi.hoisted(() => vi.fn())
vi.mock('@/api/pms/project/task-workbench', () => api)
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning: vi.fn() }) }))
const apps: { unmount: () => void }[] = []
beforeEach(() => { vi.clearAllMocks(); before.mockResolvedValue(true) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
const render = () => {
  const child = ref<any>()
  const value = ref({ task: { taskId: 10, projectId: 9, version: 3, name: '原名称', treeDepth: 0, placeholder: false, description: '<p>原模块富文本说明</p>', progress: 10 }, allowedActions: ['UPDATE', 'UPDATE_PROGRESS'] })
  const view = mount(defineComponent({ setup: () => () => h(DetailsEditor, { ref: child, workbench: value.value, beforeAction: before }) }), {}, { ElDialog: passthrough })
  apps.push(view.app)
  return { state: () => child.value.$.setupState, exposed: () => child.value, value }
}

it('keeps the edit version and does not overwrite the separate rich-text description', async () => {
  const view = render(); await nextTick(); await view.state().openEdit()
  view.state().form.name = '新名称'
  view.value.value.task.version = 4; await nextTick()
  api.updateTask.mockRejectedValue(new Error('version conflict'))
  await view.state().saveEdit()
  expect(api.updateTask).toHaveBeenCalledWith(10, { name: '新名称' }, 3)
  expect(view.state().editing).toBe(true)
  expect(view.state().form.name).toBe('新名称')
  expect(view.exposed().requestLeave()).toBe(false)
})

it('respects the shared Owner leave refusal before progress updates or opening another editor', async () => {
  const view = render(); await nextTick(); before.mockResolvedValue(false)
  await view.state().openEdit(); await view.state().saveProgress()
  expect(view.state().editing).toBe(false)
  expect(api.updateTask).not.toHaveBeenCalled()
  expect(api.updateTaskProgress).not.toHaveBeenCalled()
})

it('keeps progress updates behind server-provided actions and the task version', async () => {
  const view = render(); await nextTick(); view.state().progress = 50
  await view.state().saveProgress()
  expect(api.updateTaskProgress).toHaveBeenCalledWith(10, 50, 3)
  api.updateTaskProgress.mockClear(); view.value.value.allowedActions = []; await nextTick()
  await view.state().saveProgress(); expect(api.updateTaskProgress).not.toHaveBeenCalled()
})
