import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
vi.mock('@/api/parser-management', () => ({ parserApi: { logTypes: vi.fn(), runtime: vi.fn(), releases: vi.fn(), release: vi.fn() } }))
vi.mock('@/api/management', () => ({ managementApi: { overview: vi.fn(), collections: vi.fn(), scripts: vi.fn(), settings: vi.fn(), content: vi.fn() } }))
import { parserApi } from '@/api/parser-management'
import { managementApi } from '@/api/management'
import ParserManagementView from '@/views/management/ParserManagementView.vue'
import OverviewManagementView from '@/views/management/OverviewManagementView.vue'
const global = { plugins: [ElementPlus], stubs: { AppShell: { template: '<div><slot /></div>' }, RouterLink: { template: '<a><slot /></a>' } } }
describe('management pages', () => {
  beforeEach(() => { vi.resetAllMocks(); vi.mocked(managementApi.settings).mockRejectedValue({ response: { status: 403 } }) })
  it('renders parser empty state and independently retryable runtime failure', async () => {
    vi.mocked(parserApi.logTypes).mockResolvedValue([])
    vi.mocked(parserApi.runtime).mockRejectedValue({ response: { status: 403 } })
    const wrapper = mount(ParserManagementView, { global })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无日志类型')
    expect(wrapper.text()).toContain('403')
    expect(wrapper.text()).toContain('重试')
    wrapper.unmount()
  })
  it('renders only server authorized overview totals', async () => {
    vi.mocked(managementApi.overview).mockResolvedValue({ total: 7, byStatus: { SUCCEEDED: 5, FAILED: 2 } })
    const wrapper = mount(OverviewManagementView, { global })
    await flushPromises()
    expect(wrapper.text()).toContain('7')
    expect(wrapper.text()).toContain('成功')
    expect(wrapper.text()).not.toContain('规划中')
    expect(wrapper.find('[aria-label="总览项目"]').exists()).toBe(true)
    expect(managementApi.collections).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 5 }), expect.any(AbortSignal))
    wrapper.unmount()
  })
})
