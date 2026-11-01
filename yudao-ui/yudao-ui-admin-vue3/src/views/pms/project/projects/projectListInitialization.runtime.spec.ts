import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Projects from './index.vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import * as CompanyApi from '@/api/system/company'
import * as DeptApi from '@/api/system/dept'
import * as LocationApi from '@/api/pms/asset/location'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const grants = vi.hoisted(() => new Set<string>())
const navigate = vi.hoisted(() => vi.fn())
vi.mock('@/directives/permission/hasPermi', () => ({ hasPermission: (keys: string[]) => keys.some(key => grants.has(key)) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: navigate }) }))
vi.mock('@vueuse/core', async () => { const { ref } = await import('vue'); return { useMediaQuery: () => ref(false) } })
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: vi.fn() }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [], getDictLabel: () => '' }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn(), getProjectStatistics: vi.fn(), matchTemplates: vi.fn(), searchOfficeProjects: vi.fn(),
  createProjectMemberFilterSource: (role: string) => ({ key: `filter:${role}`, search: vi.fn(async () => ({ list: [], total: 0 })) }) }))
vi.mock('@/api/pms/project/project-templates', () => ({ getProjectTemplateRevision: vi.fn() }))
vi.mock('@/api/system/company', () => ({ getSimpleCompanyList: vi.fn() }))
vi.mock('@/api/system/dept', () => ({ getSimpleDeptList: vi.fn() }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: vi.fn(async () => []) }))
vi.mock('@/api/pms/asset/location', () => ({ getSitePage: vi.fn() }))
vi.mock('@/api/pms/customer', () => ({ getCustomerPage: vi.fn() }))
vi.mock('@/api/pms/commerce', () => ({ getContractCreationSource: vi.fn() }))
vi.mock('../inheritance/projects/customerSelection', () => ({ getSelectableCustomers: vi.fn() }))
vi.mock('./contractSelection', () => ({ getSelectableContracts: vi.fn() }))
vi.mock('../project-templates/TemplateMatchDiagnostics.vue', () => ({ default: { render: () => null } }))

const apps: Array<{ unmount: () => void }> = []
const flush = async () => { for (let i = 0; i < 8; i++) await nextTick() }
const render = async () => {
  const components = Object.fromEntries(['ElInput', 'ElSelect', 'ElOption', 'ElTable', 'ElRow', 'ElCol', 'ElRadioGroup', 'ElRadioButton', 'ElDatePicker', 'ElDivider', 'ElSteps', 'ElStep', 'PmsEntitySelect'].map(name => [name, passthrough]))
  const view = mount(Projects, {}, { ...components, ElTableColumn: tableColumn })
  apps.push(view.app)
  await flush()
  return (view.vm as any).$.setupState
}

beforeEach(() => {
  vi.clearAllMocks()
  grants.clear()
  grants.add('pms:project:query')
  vi.mocked(ProjectsApi.getProjectPage).mockResolvedValue({ list: [{ id: 7, projectName: '可见项目' }], total: 1 } as any)
  vi.mocked(ProjectsApi.getProjectStatistics).mockResolvedValue({ total: 20, overdue: 3,
    s0: 1, s1: 2, s2: 3, s3: 4, s4: 2, s5: 1, s6: 1,
    normalClosed: 3, noTrackingClosed: 2, exceptionClosed: 1 })
  vi.mocked(CompanyApi.getSimpleCompanyList).mockResolvedValue([{ id: 11, name: '公司' }] as any)
  vi.mocked(DeptApi.getSimpleDeptList).mockResolvedValue([{ id: 12, name: '办事处' }] as any)
  vi.mocked(LocationApi.getSitePage).mockResolvedValue({ list: [{ id: 13, version: 2 }] } as any)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('loads scoped projects and organization filters without requesting the site administration API', async () => {
  const state = await render()
  expect(ProjectsApi.getProjectPage).toHaveBeenCalledTimes(1)
  expect(ProjectsApi.getProjectStatistics).toHaveBeenCalledTimes(1)
  expect(state.stats).toEqual({ total: 20, overdue: 3, S0: 1, S1: 2, S2: 3, S3: 4,
    S4: 2, S5: 1, S6: 1, NORMAL_CLOSED: 3, NO_TRACKING_CLOSED: 2, EXCEPTION_CLOSED: 1 })
  expect(state.rows).toEqual([{ id: 7, projectName: '可见项目' }])
  expect(state.companies).toEqual([{ id: 11, name: '公司' }])
  expect(state.departments).toEqual([{ id: 12, name: '办事处' }])
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
})

it('requires exact criteria, retains both submitted values and opens only the explicit readonly route', async () => {
  const state = await render()
  expect(ProjectsApi.searchOfficeProjects).not.toHaveBeenCalled()
  await state.searchOffice()
  expect(ProjectsApi.searchOfficeProjects).not.toHaveBeenCalled()
  const row = { id: 9, projectName: '办事处项目', visibility: 'OFFICE_READONLY' }
  vi.mocked(ProjectsApi.searchOfficeProjects).mockResolvedValue({ list: [row], total: 1 } as any)
  state.officeQuery.projectName = '办事处项目'
  state.officeQuery.contractNo = 'Exact%_'
  await state.searchOffice(1)
  expect(ProjectsApi.searchOfficeProjects).toHaveBeenCalledWith({ projectName: '办事处项目', contractNo: 'Exact%_', pageNo: 1, pageSize: 10 })
  state.officeQuery.projectName = '未查询的新值'
  state.openOfficeProject(row)
  expect(navigate).toHaveBeenCalledWith({ path: '/pms/project/office-readonly', query: { projectId: '9', projectName: '办事处项目', contractNo: 'Exact%_' } })
  expect(state.detailVisible).toBe(false)
  expect(state.editVisible).toBe(false)
})

it('keeps list usable when statistics fail and preserves list filters on search', async () => {
  vi.mocked(ProjectsApi.getProjectStatistics).mockRejectedValueOnce(new Error('unavailable'))
  const state = await render()
  expect(state.rows).toEqual([{ id: 7, projectName: '可见项目' }])
  state.query.projectName = '测试'
  state.query.overdueOnly = false
  state.query.status = 'S1'
  state.handleSearch()
  await flush()
  expect(ProjectsApi.getProjectStatistics).toHaveBeenCalledTimes(1)
  expect(ProjectsApi.getProjectPage).toHaveBeenLastCalledWith(expect.objectContaining({
    projectName: '测试', overdueOnly: false, status: 'S1', pageNo: 1
  }))
})

it('opens the creation wizard without loading implementation sites', async () => {
  grants.add('pms:project:create')
  grants.add('pms:asset-location:query')
  const state = await render()
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
  await state.openWizard()
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
  expect(state.wizardVisible).toBe(true)
  expect(state.createForm).not.toHaveProperty('sites')
  expect(state.createForm).not.toHaveProperty('implementationLocation')
})

it('does not request sites for a creator without site query permission', async () => {
  grants.add('pms:project:create')
  const state = await render()
  await state.openWizard()
  expect(state.wizardVisible).toBe(true)
  expect(state.createForm).not.toHaveProperty('sites')
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
})

it('keeps the creation wizard closed for a project query-only user', async () => {
  const state = await render()
  await state.openWizard()
  expect(state.wizardVisible).toBe(false)
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
})
