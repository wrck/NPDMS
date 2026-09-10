import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import Panel from './ProjectClosureGuardPanel.vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import { mount, passthrough, tableColumn, findByTestId, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/project/projects', () => ({ queryTree: vi.fn(), getClosureGuard: vi.fn() }))
const apps: Array<{ unmount: () => void }> = []
const passed = { allowed: true, treeVersion: 1, blockers: [], pendingProgressProjects: [] }
const flush = async () => { for (let i = 0; i < 6; i++) await nextTick() }
const render = (initial: { projectId: number; treeVersion?: number }) => {
  const props = reactive(initial)
  const child = ref<any>()
  const host = defineComponent({ setup: () => () => h(Panel, { ...props, ref: child }) })
  const mounted = mount(host, {}, { ElInput: passthrough, ElSkeleton: passthrough, ElTable: passthrough, ElTableColumn: tableColumn })
  apps.push(mounted.app)
  return { ...mounted, props, state: () => child.value.$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(ProjectsApi.queryTree).mockResolvedValue({ treeVersion: 1 } as any)
  vi.mocked(ProjectsApi.getClosureGuard).mockResolvedValue(passed)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('presents a successful tree check as partial, never as complete closure readiness', async () => {
  const page = render({ projectId: 41, treeVersion: 1 }); await flush()
  expect(ProjectsApi.queryTree).not.toHaveBeenCalled()
  expect(ProjectsApi.getClosureGuard).toHaveBeenCalledWith(41, 1)
  expect(textOf(page.root)).toContain('项目树维度检查通过，不代表完整闭环校验通过')
  expect(textOf(page.root)).not.toContain('满足闭环前置条件')
})

it('clears the previous passed result while refreshing and after a request failure', async () => {
  const page = render({ projectId: 41, treeVersion: 1 }); await flush()
  expect(findByTestId(page.root, 'closure-tree-passed')).toBeDefined()
  vi.mocked(ProjectsApi.getClosureGuard).mockRejectedValueOnce(new Error('unavailable'))
  const pending = page.state().evaluate()
  expect(page.state().result).toBeUndefined()
  await pending; await flush()
  expect(findByTestId(page.root, 'closure-tree-passed')).toBeUndefined()
  expect(textOf(page.root)).toContain('不能沿用上次通过结果')
})

it('does not issue a guard check with a previous project tree response', async () => {
  let finishOld!: (value: any) => void
  vi.mocked(ProjectsApi.queryTree).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    .mockResolvedValueOnce({ treeVersion: 2 } as any)
  vi.mocked(ProjectsApi.getClosureGuard).mockResolvedValue({ ...passed, allowed: false, treeVersion: 2,
    blockers: [{ projectId: 43, projectCode: 'CHILD-43', projectName: '子项目43', blockerType: 'EXECUTING' }] })
  const page = render({ projectId: 41 }); await flush()
  page.props.projectId = 42; await flush()
  finishOld({ treeVersion: 1 }); await flush()
  expect(ProjectsApi.getClosureGuard).toHaveBeenCalledExactlyOnceWith(42, 2)
  expect(page.state().result.treeVersion).toBe(2)
  expect(page.state().result.allowed).toBe(false)
  expect(textOf(page.root)).toContain('项目树检查存在阻断项')
})

it('rejects invalid explicit project or tree versions instead of silently querying latest', async () => {
  for (const props of [{ projectId: 0 }, { projectId: 41, treeVersion: 0 }]) {
    const page = render(props); await flush()
    expect(textOf(page.root)).toContain('项目或项目树版本无效')
  }
  expect(ProjectsApi.queryTree).not.toHaveBeenCalled()
  expect(ProjectsApi.getClosureGuard).not.toHaveBeenCalled()
})
