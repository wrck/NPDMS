import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import ProjectCustomerOverview from './ProjectCustomerOverview.vue'
import * as CustomerApi from '@/api/pms/customer'
import { mount, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/customer', () => ({
  getProjectCustomerOverview: vi.fn(), getCustomerByCode: vi.fn(), getCustomer: vi.fn()
}))
vi.mock('@/api/pms/customer/service-level', () => ({ getServiceLevelPage: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => false }))
vi.mock('@/views/pms/customer/contacts/index.vue', () => ({ default: defineComponent({ setup: () => () => h('contacts') }) }))
vi.mock('@/views/pms/customer/components/CustomerFormDrawer.vue', () => ({ default: defineComponent({ setup: () => () => h('drawer') }) }))

beforeEach(() => vi.clearAllMocks())
const settle = async () => { await nextTick(); await nextTick(); await nextTick() }

it('loads the server-authorized project association instead of the global customer-code query', async () => {
  vi.mocked(CustomerApi.getProjectCustomerOverview).mockResolvedValue({
    id: '2102227613952667649', code: 'C-200', name: '项目所属客户', lifecycleStatus: 'ENABLED'
  } as unknown as CustomerApi.CustomerRespVO)
  const projectId = '992203060198'
  const { root, app } = mount(ProjectCustomerOverview, { project: { id: projectId, customerCode: 'untrusted-code' } })
  await settle()
  expect(CustomerApi.getProjectCustomerOverview).toHaveBeenCalledWith(projectId)
  expect(CustomerApi.getCustomerByCode).not.toHaveBeenCalled()
  expect(textOf(root)).toContain('项目所属客户')
  app.unmount()
})

it('can load a bound customer even when the project response omits its code', async () => {
  vi.mocked(CustomerApi.getProjectCustomerOverview).mockResolvedValue({ id: 200, code: 'C-200', name: '绑定客户' } as CustomerApi.CustomerRespVO)
  const { root, app } = mount(ProjectCustomerOverview, { project: { id: '992203060198' } })
  await settle()
  expect(textOf(root)).toContain('绑定客户')
  app.unmount()
})

it('shows an unassociated project without falling back to a global lookup', async () => {
  vi.mocked(CustomerApi.getProjectCustomerOverview).mockResolvedValue(null)
  const { root, app } = mount(ProjectCustomerOverview, { project: { id: '992203060198' } })
  await settle()
  expect(textOf(root)).toContain('当前项目尚未关联客户')
  expect(CustomerApi.getCustomerByCode).not.toHaveBeenCalled()
  app.unmount()
})

it('keeps the error visible when project access is refused', async () => {
  vi.mocked(CustomerApi.getProjectCustomerOverview).mockRejectedValue(new Error('project scope denied'))
  const { root, app } = mount(ProjectCustomerOverview, { project: { id: '992203060198' } })
  await settle()
  expect(textOf(root)).toContain('客户详细信息暂未获取，请稍后重试。')
  expect(textOf(root)).not.toContain('当前项目尚未关联客户')
  expect(CustomerApi.getCustomerByCode).not.toHaveBeenCalled()
  app.unmount()
})
