import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive, ref } from 'vue'
import Workbench from './index.vue'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import { mount, passthrough, tableColumn, findByTestId, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const detailState = vi.hoisted(() => ({ dirty: false, close: vi.fn() }))
const guards = vi.hoisted(() => ({ leave: [] as Array<() => Promise<boolean>>, update: [] as Array<() => Promise<boolean>> }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), onBeforeRouteLeave: (guard: () => Promise<boolean>) => guards.leave.push(guard), onBeforeRouteUpdate: (guard: () => Promise<boolean>) => guards.update.push(guard) }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/project/acceptance-report', () => ({ getActivities: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('./detail.vue', () => ({ default: { setup: (_: unknown, { expose }: any) => {
  expose({
    requestLeave: async () => !detailState.dirty,
    discardChanges: () => { if (detailState.dirty) return false; detailState.close(); return true },
    isDirty: () => detailState.dirty
  }); return () => null
} } }))
const warning = vi.hoisted(() => vi.fn())
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ warning }) }))
const apps: Array<{ unmount: () => void }> = []
const flush = async () => { for (let i = 0; i < 6; i++) await nextTick() }
const render = (id: number) => {
  const props = reactive({ projectId: id, projectName: '验收项目' })
  const child = ref<any>()
  const wrapper = defineComponent({ setup: () => () => h(Workbench, { ...props, ref: child }) })
  const mounted = mount(wrapper, {}, { ElInput: passthrough, PmsEntitySelect: passthrough, ElSkeleton: passthrough, ElTable: passthrough, ElTableColumn: tableColumn })
  apps.push(mounted.app)
  return { ...mounted, props, child, state: () => child.value.$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks(); detailState.dirty = false; guards.leave.length = 0; guards.update.length = 0
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  vi.mocked(ReportApi.getActivities).mockResolvedValue([])
})
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.unstubAllGlobals() })

it('locks contextual queries to the host project and never uses a manually changed filter', async () => {
  const page = render(41); await flush()
  expect(ReportApi.getActivities).toHaveBeenCalledWith(41)
  page.state().query.projectId = 99
  await page.state().load()
  expect(ReportApi.getActivities).toHaveBeenLastCalledWith(41)
})

it('does not turn an invalid project context into a global query', async () => {
  const page = render(0); await flush()
  expect(ReportApi.getActivities).not.toHaveBeenCalled()
  expect(textOf(page.root)).toContain('项目上下文无效')
})

it('ignores late activities from the previous project and closes its detail view', async () => {
  let finishOld!: (value: any) => void
  vi.mocked(ReportApi.getActivities).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    .mockResolvedValueOnce([{ id: 2, projectId: 42, acceptanceType: 'FINAL', activityStatus: 'PENDING', version: 1 } as any])
  const page = render(41); await flush()
  page.props.projectId = 42; await flush()
  finishOld([{ id: 1, projectId: 41 }]); await flush()
  const rows = findByTestId(page.root, 'acceptance-activities')?.props?.data as any[]
  expect(rows.map(row => row.projectId)).toEqual([42])
  expect(detailState.close).toHaveBeenCalled()
})

it('keeps a service failure distinct from an empty or completed activity list', async () => {
  vi.mocked(ReportApi.getActivities).mockRejectedValue(new Error('unavailable'))
  const page = render(41); await flush()
  expect(textOf(page.root)).toContain('验收活动加载失败')
  expect(findByTestId(page.root, 'acceptance-activities')?.props?.['empty-text']).toBe('验收活动未加载成功')
})

it('protects editing on navigation without preventing a read-only list refresh', async () => {
  const page = render(41); await flush()
  detailState.dirty = true
  expect(await page.child.value.requestLeave()).toBe(false)
  expect(await guards.leave.at(-1)!()).toBe(false)
  expect(await guards.update.at(-1)!()).toBe(false)
  await page.state().load()
  expect(ReportApi.getActivities).toHaveBeenCalledTimes(2)
})
