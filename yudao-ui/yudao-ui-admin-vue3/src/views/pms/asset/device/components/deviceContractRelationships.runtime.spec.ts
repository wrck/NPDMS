// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, expect, it, vi } from 'vitest'
import Panel from './DeviceContractRelationshipPanel.vue'
import { getDeviceContractRelationships } from '@/api/pms/asset/device'
vi.mock('@/api/pms/asset/device', () => ({ getDeviceContractRelationships: vi.fn() }))
let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.clearAllMocks(); document.body.innerHTML = '' })
it('shows both device products and the contract without synthesizing ownership', async () => {
  vi.mocked(getDeviceContractRelationships).mockResolvedValue({ list: [{
    id: '2104783611154759682', sourceDeviceSn: 'MAIN', targetDeviceSn: 'EXTRA',
    sourceItem: 'P1', targetItem: 'P2', contractNo: 'CONTRACT',
    effectiveFrom: '2019-11-14 08:31:39.690', effectiveTo: null,
    createTime: '2026-09-29 12:01:32.544', updateTime: '2026-09-29 12:01:32.544'
  }], total: 1 })
  wrapper = mount(Panel, { props: { deviceId: 90 }, global: { plugins: [ElementPlus] } })
  await flushPromises()
  for (const value of ['MAIN', 'EXTRA', 'P1', 'P2', 'CONTRACT']) expect(wrapper.text()).toContain(value)
  expect(getDeviceContractRelationships).toHaveBeenCalledWith(90, { pageNo: 1, pageSize: 20 })
})
it('does not leave stale rows when access fails and allows retry', async () => {
  vi.mocked(getDeviceContractRelationships).mockRejectedValueOnce(new Error('forbidden'))
    .mockResolvedValueOnce({ list: [], total: 0 })
  wrapper = mount(Panel, { props: { deviceId: 90 }, global: { plugins: [ElementPlus] } })
  await flushPromises()
  expect(wrapper.text()).toContain('设备关系加载失败')
  await wrapper.find('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).not.toContain('设备关系加载失败')
  expect(wrapper.text()).toContain('暂无主SN与附加SN关系')
})
it('ignores a late response from the previously opened device', async () => {
  let firstResolve: (value: any) => void = () => {}
  vi.mocked(getDeviceContractRelationships).mockImplementationOnce(() => new Promise(resolve => { firstResolve = resolve }))
    .mockResolvedValueOnce({ list: [{ id: 2, sourceDeviceSn: 'NEW', targetDeviceSn: 'NEW-EXTRA' }], total: 1 })
  wrapper = mount(Panel, { props: { deviceId: 90 }, global: { plugins: [ElementPlus] } })
  await wrapper.setProps({ deviceId: 91 })
  await flushPromises()
  firstResolve({ list: [{ id: 1, sourceDeviceSn: 'OLD', targetDeviceSn: 'OLD-EXTRA' }], total: 1 })
  await flushPromises()
  expect(wrapper.text()).toContain('NEW-EXTRA')
  expect(wrapper.text()).not.toContain('OLD-EXTRA')
})
