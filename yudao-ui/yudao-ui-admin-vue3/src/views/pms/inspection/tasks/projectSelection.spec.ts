import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import InspectionTasks from './index.vue'
import { findByTestId, mount, textOf } from '@/views/pms/cutover/cutoverTestHarness'
const projectApi = vi.hoisted(() => ({ searchProjectOptions: vi.fn(), getProjectContext: vi.fn(), getProjectDevicePage: vi.fn() }))
vi.mock('@/api/pms/project/selection', () => projectApi)
vi.mock('@/api/pms/inspection/tasks', () => ({}))
vi.mock('@/utils', () => ({ generateUUID: () => 'test-id' }))
vi.mock('@/utils/permission', () => ({ checkPermi: (permissions: string[]) => permissions.every(p => p.startsWith('pms:inspection-task:')) }))
vi.mock('@/utils/formatTime', () => ({ formatDate: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ error: vi.fn(), success: vi.fn(), confirm: vi.fn() }) }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ref(false) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/pms/inspection/tasks', query: { create: '1' } }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }), onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
const form = defineComponent({ setup(_, { slots, expose }) {
  expose({ clearValidate: vi.fn() }); return () => h('section', slots.default?.())
} })
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
beforeEach(() => {
  vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
  projectApi.searchProjectOptions.mockResolvedValue([{ id: '101', code: 'P-101', name: '授权项目' }])
  projectApi.getProjectContext.mockResolvedValue({ project: { projectId: '101', projectCode: 'P-101', projectName: '授权项目',
    customerName: '客户', projectManagerName: '项目经理', serviceManagerNames: '服务经理', industryName: '金融', departmentName: '办事处', salesNames: '销售' }, customerServiceLevel: '一级' })
  projectApi.getProjectDevicePage.mockResolvedValue({ list: [{ deviceId: '401', serialNumber: 'SN-401', productCode: 'CODE-401', productModel: 'MODEL-401' }], total: 1 })
})
afterEach(() => vi.unstubAllGlobals())

it('loads all project fields and device candidates from project APIs without project management permission', async () => {
  const openedAt = Date.now()
  const view = mount(InspectionTasks, {}, { ElForm: form }); await flush()
  const plannedAt = findByTestId(view.root, 'inspection-create-planned-at')!.props!.modelValue as Date
  expect(plannedAt.getTime()).toBeGreaterThanOrEqual(openedAt)
  expect(plannedAt.getTime()).toBeLessThanOrEqual(Date.now())
  const select = findByTestId(view.root, 'inspection-create-project-select')!
  const updateProject = select.props!['onUpdate:modelValue'] as (value: string) => void
  const changeProject = select.props!.onChange as () => void
  updateProject('101'); changeProject(); await flush()
  expect(projectApi.searchProjectOptions).toHaveBeenCalledWith('')
  expect(projectApi.getProjectContext).toHaveBeenCalledWith('101')
  await (findByTestId(view.root, 'choose-project-devices')!.props!.onClick as Function)(); await flush()
  expect(projectApi.getProjectDevicePage).toHaveBeenCalledWith('101', { keyword: '', pageNo: 1, pageSize: 20 })
  expect(textOf(view.root)).toContain('服务经理')
  expect(textOf(view.root)).toContain('办事处')
  expect(textOf(view.root)).toContain('一级')
  expect(textOf(view.root)).not.toContain('任务名称')
  expect(textOf(view.root)).not.toContain('无权读取完整项目资料')
  view.app.unmount()
})
