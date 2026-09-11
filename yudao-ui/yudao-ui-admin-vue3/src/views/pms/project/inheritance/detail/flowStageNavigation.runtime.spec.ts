import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import StageNavigation from './ProjectFlowStageNavigation.vue'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const api = vi.hoisted(() => ({ getProjectTasks: vi.fn() }))
vi.mock('@/api/pms/project/task-workbench', () => api)
const flush = async () => { for (let i = 0; i < 5; i++) { await Promise.resolve(); await nextTick() } }
beforeEach(() => vi.clearAllMocks())

it('loads stage roots only on expansion, then direct children using their actual parent identity', async () => {
  const first = { taskId: 1, name: '父任务', stageCode: 'S4', treeDepth: 0, placeholder: false }
  api.getProjectTasks.mockResolvedValueOnce({ rows: [first], taskTreeVersion: 1 })
    .mockResolvedValueOnce({ rows: [{ ...first, taskId: 2, name: '深层任务', parentTaskId: 1 }], taskTreeVersion: 1 })
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(StageNavigation, {
    ref: child, projectId: 9, stage: { stageCode: 'S4', stageName: '实施', stageStatus: 'ACTIVE', taskCount: 2 }, refreshVersion: 1
  }) })
  const view = mount(wrapper)
  try {
    expect(api.getProjectTasks).not.toHaveBeenCalled()
    await child.value.$.setupState.toggleStage(); await flush()
    expect(api.getProjectTasks).toHaveBeenCalledWith(9, expect.objectContaining({ mode: 'DIRECT_CHILDREN', stageCode: 'S4' }))
    expect(textOf(view.root)).toContain('父任务')
    await child.value.$.setupState.toggleTask(first); await flush()
    expect(api.getProjectTasks).toHaveBeenLastCalledWith(9, expect.objectContaining({ mode: 'DIRECT_CHILDREN', stageCode: 'S4', parentTaskId: 1 }))
    expect(textOf(view.root)).toContain('深层任务')
    await child.value.$.setupState.toggleTask(first); await flush()
    expect(textOf(view.root)).not.toContain('深层任务')
  } finally { view.app.unmount() }
})
