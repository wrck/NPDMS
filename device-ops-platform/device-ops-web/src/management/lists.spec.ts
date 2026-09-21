import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
vi.mock('@/api/management', () => ({ managementApi: { collections: vi.fn(), scripts: vi.fn(), content: vi.fn() } }))
vi.mock('@/api/parser-management', () => ({ parserApi: { tasks: vi.fn(), cancel: vi.fn(), terminate: vi.fn() } }))
import { managementApi } from '@/api/management'
import { parserApi } from '@/api/parser-management'
import CollectionList from '@/components/management/CollectionList.vue'
import ParseTasksPanel from '@/components/management/ParseTasksPanel.vue'
const global = { plugins: [ElementPlus], stubs: { RouterLink: { template: '<a><slot /></a>' } } }
describe('task lists', () => {
  it('loads authorized summaries without hard cancel or rerun controls', async () => {
    vi.mocked(managementApi.collections).mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
    const wrapper = mount(CollectionList, { global }); await flushPromises()
    expect(managementApi.collections).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }), expect.any(AbortSignal))
    expect(wrapper.text()).toContain('暂无采集记录')
    expect(wrapper.text()).not.toContain('硬取消')
    expect(wrapper.text()).not.toContain('重执行')
    wrapper.unmount()
  })
  it('shows parser waiting reason and cursor pagination', async () => {
    vi.mocked(parserApi.tasks).mockResolvedValue([{ taskId: 't1', state: 'WAITING', waitReason: 'NO_CAPABLE_WORKER', logType: 'x' } as never])
    const wrapper = mount(ParseTasksPanel, { global }); await flushPromises()
    expect(wrapper.text()).toContain('NO_CAPABLE_WORKER')
    expect(wrapper.text()).toContain('终止等待')
    expect(wrapper.text()).toContain('下一页')
    expect(parserApi.terminate).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
