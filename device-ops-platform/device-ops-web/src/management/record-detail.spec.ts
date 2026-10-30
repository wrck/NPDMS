import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
vi.mock('@/config/runtime', () => ({ loadRuntimeConfig: async () => ({ authMode: 'local' }) }))
vi.mock('@/api/device-ops', () => ({ getGenericCollection: vi.fn(), getCollection: vi.fn(), getCollectionSemanticResults: vi.fn(), submitCollection: vi.fn(), submitGenericCollection: vi.fn() }))
vi.mock('@/api/collection-output-stream', () => ({ streamCollectionOutput: vi.fn() }))
import { getGenericCollection, getCollectionSemanticResults } from '@/api/device-ops'
import router from '@/router'
import RecordsManagementView from '@/views/management/RecordsManagementView.vue'
import CollectionTaskPanel from '@/components/CollectionTaskPanel.vue'
describe('record deep link real panel restoration', () => {
  afterEach(() => window.history.replaceState({}, '', '/'))
  it('keeps the default workbench submit button and event', async () => {
    window.history.replaceState({}, '', '/projects/direct')
    const wrapper = mount(CollectionTaskPanel, { global: { plugins: [ElementPlus] } })
    const button = wrapper.findAll('button').find(item => item.text() === '连接并执行采集')!
    expect(button).toBeDefined()
    await button.trigger('click')
    expect(wrapper.emitted('submit')).toHaveLength(1)
    expect(wrapper.find('.task-panel__scope').exists()).toBe(false)
    wrapper.unmount()
  })
  it('shows a read-only empty message without execution instructions', async () => {
    window.history.replaceState({}, '', '/records/missing')
    const wrapper = mount(CollectionTaskPanel, { props: { readonly: true }, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('连接并执行')
    expect(wrapper.text()).not.toContain('填写连接参数')
    expect(wrapper.text()).toContain('请从记录列表选择记录')
    wrapper.unmount()
  })
  it('loads the collection through the existing panel and displays semantic empty state', async () => {
    vi.mocked(getGenericCollection).mockResolvedValue({ collectionId: 'history-1', namespace: 'n', status: 'SUCCEEDED', targets: [], parsedFacts: {} } as never)
    vi.mocked(getCollectionSemanticResults).mockResolvedValue([])
    await router.push('/records/history-1?namespace=n&mode=generic')
    const wrapper = mount(RecordsManagementView, { global: { plugins: [ElementPlus, router], stubs: { AppShell: { template: '<div><slot /></div>' } } } })
    await flushPromises()
    expect(getGenericCollection).toHaveBeenCalledWith('n', 'history-1', expect.any(AbortSignal))
    expect(wrapper.text()).toContain('history-1')
    expect(wrapper.text()).not.toContain('连接并执行采集')
    expect(wrapper.text()).not.toContain('项目和设备均可选')
    expect(wrapper.text()).toContain('下载完整记录')
    wrapper.unmount()
  })
})
