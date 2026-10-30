import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import InspectionTasks from './index.vue'
import { findByTestId, mount, textOf } from '@/views/pms/cutover/cutoverTestHarness'
const projectApi = vi.hoisted(() => ({ searchProjectOptions: vi.fn(), getProjectContext: vi.fn(), getProjectDevicePage: vi.fn() }))
const inspectionApi = vi.hoisted(() => ({ createTask: vi.fn() }))
vi.mock('@/api/pms/project/selection', () => projectApi)
vi.mock('@/api/pms/inspection/tasks', () => inspectionApi)
vi.mock('@/utils', () => ({ generateUUID: () => 'test-id' }))
vi.mock('@/utils/permission', () => ({ checkPermi: (permissions: string[]) => permissions.every(p => p.startsWith('pms:inspection-task:')) }))
vi.mock('@/utils/formatTime', () => ({ formatDate: vi.fn() }))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: { PMS_MAJOR_PROJECT_LEVEL: 'pms_major_project_level' },
  getDictLabel: (_type: string, value: unknown) => String(value ?? '')
}))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ error: vi.fn(), success: vi.fn(), confirm: vi.fn() }) }))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ref(false) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/pms/inspection/tasks', query: { create: '1' } }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }), onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
const form = defineComponent({ setup(_, { slots, expose }) {
  expose({ clearValidate: vi.fn(), validate: vi.fn(async () => true) }); return () => h('section', slots.default?.())
} })
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
beforeEach(() => {
  inspectionApi.createTask.mockReset()
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

it('shows the actual missing-customer rejection and keeps preparation editable without pretending creation succeeded', async () => {
  inspectionApi.createTask.mockRejectedValue({ response: { status: 400, data: {
    msg: '项目尚未关联客户资料', data: { reasonCode: 'INVALID_REQUEST' }
  } } })
  const view = mount(InspectionTasks, {}, { ElForm: form }); await flush()
  const state = (view.vm as any).$.setupState
  Object.assign(state.form, { projectId: '53999', deviceIds: ['401'], background: '现场巡检', mode: 'OFFLINE' })
  await state.submitCreate(); await flush()
  expect(textOf(view.root)).toContain('项目尚未关联客户资料')
  expect(state.form.projectId).toBe('53999')
  expect(state.form.background).toBe('现场巡检')
  expect(state.pendingCreate).toBeNull()
  expect(state.submitting).toBe(false)
  expect(state.createVisible).toBe(true)
  view.app.unmount()
})

it('retains the same request and preparation after an unknown creation outcome', async () => {
  inspectionApi.createTask.mockRejectedValue(new Error('Network Error'))
  const view = mount(InspectionTasks, {}, { ElForm: form }); await flush()
  const state = (view.vm as any).$.setupState
  Object.assign(state.form, { projectId: '101', deviceIds: ['401'], background: '现场巡检', mode: 'OFFLINE' })
  await state.submitCreate(); await flush()
  const original = state.pendingCreate
  expect(textOf(view.root)).toContain('暂时无法确认创建结果')
  await state.submitCreate(); await flush()
  expect(state.pendingCreate).toBe(original)
  expect(inspectionApi.createTask).toHaveBeenCalledTimes(2)
  expect(inspectionApi.createTask.mock.calls[1]).toEqual(inspectionApi.createTask.mock.calls[0])
  expect(state.form.background).toBe('现场巡检')
  view.app.unmount()
})

it('offers an optional customer-name snapshot when the project has no name and sends no customer identity', async () => {
  projectApi.getProjectContext.mockResolvedValue({ project: { projectId: '101', projectName: '无客户项目' } })
  inspectionApi.createTask.mockRejectedValue({ response: { status: 400, data: { msg: '仅检查提交内容' } } })
  const view = mount(InspectionTasks, {}, { ElForm: form }); await flush()
  const state = (view.vm as any).$.setupState
  state.form.projectId = '101'; await flush()
  const input = findByTestId(view.root, 'inspection-create-customer-name')!
  expect(input).toBeDefined()
  expect(input.props?.maxlength).toBe(300)
  ;(input.props!['onUpdate:modelValue'] as Function)('  现场客户  ')
  Object.assign(state.form, { deviceIds: ['401'], background: '现场巡检' })
  await state.submitCreate()
  expect(inspectionApi.createTask.mock.calls[0][0]).toMatchObject({ customerName: '现场客户' })
  expect(inspectionApi.createTask.mock.calls[0][0]).not.toHaveProperty('customerId')
  expect(inspectionApi.createTask.mock.calls[0][0]).not.toHaveProperty('customerCode')
  view.app.unmount()
})

it('does not send a manual name over the authoritative project name and clears it when switching projects', async () => {
  inspectionApi.createTask.mockRejectedValue({ response: { status: 400, data: { msg: '仅检查提交内容' } } })
  const view = mount(InspectionTasks, {}, { ElForm: form }); await flush()
  const state = (view.vm as any).$.setupState
  Object.assign(state.form, { projectId: '101', deviceIds: ['401'], background: '现场巡检', customerName: '旧项目草稿' })
  await flush()
  expect(findByTestId(view.root, 'inspection-create-customer-name')).toBeUndefined()
  await state.submitCreate()
  expect(inspectionApi.createTask.mock.calls[0][0]).not.toHaveProperty('customerName')
  ;(findByTestId(view.root, 'inspection-create-project-select')!.props!.onChange as Function)()
  expect(state.form.customerName).toBe('')
  expect(state.form.deviceIds).toEqual([])
  view.app.unmount()
})
