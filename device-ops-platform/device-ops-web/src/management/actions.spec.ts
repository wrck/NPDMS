import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
vi.mock('@/management/access', () => ({ useAccess: () => ({ can: () => true }) }))
vi.mock('@/api/parser-management', () => ({ parserApi: { logTypes: vi.fn(), runtime: vi.fn(), releases: vi.fn(), release: vi.fn(), active: vi.fn(), activate: vi.fn(), submit: vi.fn() } }))
vi.mock('@/api/management', () => ({ managementApi: { settings: vi.fn() } }))
import { parserApi } from '@/api/parser-management'
import { managementApi } from '@/api/management'
import ParserManagementView from '@/views/management/ParserManagementView.vue'
const global = { plugins: [ElementPlus], stubs: { AppShell: { template: '<div><slot /></div>' } } }
describe('parser explicit mutations', () => {
  it('keeps the fetched binding through confirmation and never activates on cancellation', async () => {
    vi.mocked(parserApi.logTypes).mockResolvedValue([])
    vi.mocked(parserApi.runtime).mockRejectedValue({ response: { status: 403 } })
    vi.mocked(managementApi.settings).mockRejectedValue({ response: { status: 403 } })
    vi.mocked(parserApi.releases).mockResolvedValue([])
    vi.mocked(parserApi.active).mockResolvedValue({ logType: 'x', releaseId: 'active-1' })
    vi.mocked(parserApi.release).mockResolvedValue({ releaseId: 'r2', state: 'PUBLISHED' } as never)
    const wrapper = mount(ParserManagementView, { global })
    const vm = wrapper.vm as unknown as { selectedType: string; selectedRelease: string; changeBinding(): Promise<void> }
    vm.selectedType = 'x'; await flushPromises(); vm.selectedRelease = 'r2'; await flushPromises()
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce('cancel')
    await vm.changeBinding()
    expect(parserApi.activate).not.toHaveBeenCalled()
    confirm.mockResolvedValueOnce('confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>)
    await vm.changeBinding()
    expect(parserApi.activate).toHaveBeenCalledWith('x', 'r2', 'active-1')
    wrapper.unmount(); confirm.mockRestore()
  })
})
