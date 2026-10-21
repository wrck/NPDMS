import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Projects from './index.vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import * as CompanyApi from '@/api/system/company'
import * as DeptApi from '@/api/system/dept'
import * as LocationApi from '@/api/pms/asset/location'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const grants = vi.hoisted(() => new Set<string>())
vi.mock('@/directives/permission/hasPermi', () => ({ hasPermission: (keys: string[]) => keys.some(key => grants.has(key)) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@vueuse/core', async () => { const { ref } = await import('vue'); return { useMediaQuery: () => ref(false) } })
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: vi.fn() }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [], getDictLabel: () => '' }))
vi.mock('@/api/pms/project/projects', () => ({ getProjectPage: vi.fn(), matchTemplates: vi.fn() }))
vi.mock('@/api/pms/project/project-templates', () => ({ getProjectTemplateRevision: vi.fn() }))
vi.mock('@/api/system/company', () => ({ getSimpleCompanyList: vi.fn() }))
vi.mock('@/api/system/dept', () => ({ getSimpleDeptList: vi.fn() }))
vi.mock('@/api/system/user', () => ({ getSimpleUserList: vi.fn(async () => []) }))
vi.mock('@/api/pms/asset/location', () => ({ getSitePage: vi.fn() }))
vi.mock('@/api/pms/customer', () => ({ getCustomerPage: vi.fn() }))
vi.mock('@/api/pms/commerce', () => ({ getContractCreationSource: vi.fn() }))
vi.mock('../inheritance/projects/customerSelection', () => ({ getSelectableCustomers: vi.fn() }))
vi.mock('./contractSelection', () => ({ getSelectableContracts: vi.fn() }))
vi.mock('../inheritance/projects/CustomerCorrectionDialog.vue', () => ({ default: { render: () => null } }))
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
  vi.mocked(CompanyApi.getSimpleCompanyList).mockResolvedValue([{ id: 11, name: '公司' }] as any)
  vi.mocked(DeptApi.getSimpleDeptList).mockResolvedValue([{ id: 12, name: '办事处' }] as any)
  vi.mocked(LocationApi.getSitePage).mockResolvedValue({ list: [{ id: 13, version: 2 }] } as any)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('loads scoped projects and organization filters without requesting the site administration API', async () => {
  const state = await render()
  expect(ProjectsApi.getProjectPage).toHaveBeenCalled()
  expect(state.rows).toEqual([{ id: 7, projectName: '可见项目' }])
  expect(state.companies).toEqual([{ id: 11, name: '公司' }])
  expect(state.departments).toEqual([{ id: 12, name: '办事处' }])
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
})

it('loads sites and preserves their versions when an authorized creator opens the wizard', async () => {
  grants.add('pms:project:create')
  grants.add('pms:asset-location:query')
  const state = await render()
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
  await state.openWizard()
  expect(LocationApi.getSitePage).toHaveBeenCalledTimes(1)
  expect(state.wizardVisible).toBe(true)
  expect(state.availableSites).toEqual([{ id: 13, version: 2 }])
  const row = { siteId: 13, siteVersion: undefined as number | undefined }
  state.syncSiteVersion(row)
  expect(row.siteVersion).toBe(2)
})

it('does not request sites for a creator without site query permission', async () => {
  grants.add('pms:project:create')
  const state = await render()
  await state.openWizard()
  expect(state.wizardVisible).toBe(true)
  expect(state.availableSites).toEqual([])
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
})

it('keeps the creation wizard closed for a project query-only user', async () => {
  const state = await render()
  await state.openWizard()
  expect(state.wizardVisible).toBe(false)
  expect(LocationApi.getSitePage).not.toHaveBeenCalled()
})
