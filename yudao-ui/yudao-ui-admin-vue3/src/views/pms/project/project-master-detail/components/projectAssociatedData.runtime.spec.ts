// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import Panel from './ProjectAssociatedDataPanel.vue'
import { getAssociatedData } from '@/api/pms/project/associated-data'
import ShipmentPanel from '../../../asset/device/components/DeviceShipmentPanel.vue'
import { getDeviceShipments } from '@/api/pms/asset/device'
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/api/pms/project/associated-data', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/pms/project/associated-data')>()),
  getAssociatedData: vi.fn()
}))
vi.mock('@/api/pms/asset/device', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/pms/asset/device')>()),
  getDeviceShipments: vi.fn()
}))
const row = {
  id: '999999999999999999',
  sourceKey: '301',
  values: {
    productCode: 'NATIVE-PRODUCT',
    productName: '原项目产品',
    projectQuantity: '12',
    unitCode: null
  }
}
let wrapper: ReturnType<typeof mount> | undefined
beforeEach(() =>
  vi
    .mocked(getAssociatedData)
    .mockReset()
    .mockResolvedValue({ list: [row], total: 1 })
)
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})
const render = () =>
  mount(Panel, { props: { projectId: '100' }, global: { plugins: [ElementPlus] } })
it('shows only the requested formal business categories', async () => {
  wrapper = mount(Panel, { props: { projectId: '100', title: '产品清单', kinds: ['real-products', 'lease-products', 'configured-products'] }, global: { plugins: [ElementPlus] } })
  await flushPromises()
  expect(wrapper.text()).toContain('产品清单')
  expect(wrapper.text()).toContain('真实产品清单')
  expect(wrapper.text()).toContain('租赁产品')
  expect(wrapper.text()).not.toContain('安装地址来源')
  expect(wrapper.text()).not.toContain('许可历史')
  expect(getAssociatedData).toHaveBeenCalledWith('100', 'real-products', 1, 20)
})
it('reads project details from formal fields without old source mirrors', async () => {
  vi.mocked(getAssociatedData).mockResolvedValue({
    list: [{
      id: '100', sourceKey: '301',
      values: {
        projectType: '10', projectCode: '16200723010901N-0', projectName: '正式项目',
        lifecycleStatus: 'ACTIVE', currentStage: 'S4', departmentCode: '162007',
        customInfo: '{"original":[1,{"empty":null}]}', creator: '正式创建人', updater: null,
        orderCreateTime: '2023-02-24T11:19:34', projectStartTime: '2023-03-07T22:37:01',
        projectRefreshTime: '2024-12-27T16:08:03', closedAt: '2025-01-22T03:25:08',
        sourceProjectCode: 'OBSOLETE-MIRROR', sourceCustomConfig: 'OBSOLETE-CONFIG'
      }
    }], total: 1
  })
  wrapper = render()
  await flushPromises()
  const projectTab = wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '项目附加信息')!
  await projectTab.trigger('click')
  await flushPromises()
  expect(getAssociatedData).toHaveBeenLastCalledWith('100', 'project', 1, 20)
  expect(wrapper.text()).toContain('16200723010901N-0')
  await wrapper.get('button.el-button--primary').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('正式创建人')
  expect(wrapper.text()).toContain('original')
  expect(wrapper.text()).toContain('2023-02-24 11:19:34.000')
  expect(wrapper.text()).toContain('2023-03-07 22:37:01.000')
  expect(wrapper.text()).toContain('2024-12-27 16:08:03.000')
  expect(wrapper.text()).toContain('2025-01-22 03:25:08.000')
  expect(wrapper.text()).not.toContain('OBSOLETE-MIRROR')
  expect(wrapper.text()).not.toContain('OBSOLETE-CONFIG')
})
it('shows the formal order customer date instead of epoch milliseconds', async () => {
  const customerRequiredTime = new Date('2023-03-20T00:00:00').getTime()
  vi.mocked(getAssociatedData).mockResolvedValue({ list: [{ id: '100', sourceKey: 'O',
    values: { contractNo: 'C', sourceSystem: 'D365', companyCode: '001', customerRequiredTime,
      orderComment: '', legacyRecordId: 'REMOVED-MIRROR' } }], total: 1 })
  wrapper = render()
  await flushPromises()
  await wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '销售订单')!.trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('2023-03-20 00:00:00.000')
  expect(wrapper.text()).not.toContain(String(customerRequiredTime))
  await wrapper.get('button.el-button--primary').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('D365')
  expect(wrapper.text()).not.toContain('REMOVED-MIRROR')
})
it('reads native project products with absent unit and shows detail', async () => {
  wrapper = render()
  await flushPromises()
  expect(getAssociatedData).toHaveBeenCalledWith('100', 'products', 1, 20)
  expect(wrapper.text()).toContain('NATIVE-PRODUCT')
  await wrapper.get('button.el-button--primary').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('单位')
  expect(wrapper.text()).toContain('—')
})
it('shows formal historical scope facts without presenting them as confirmed allocation', async () => {
  vi.mocked(getAssociatedData).mockResolvedValue({ list: [{ ...row, values: {
    ...row.values, orderQuantity: '14', deliveredQuantity: '3', openQuantity: '11',
    allocationConfirmed: false, scopeStatus: 'ACTIVE', allocationVersion: 1,
    effectiveFrom: '2026-10-10T02:30:00', departmentCode: '162007', departmentName: '长春办事处'
  } }], total: 1 })
  wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('3')
  await wrapper.get('button.el-button--primary').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('已确认分配')
  expect(wrapper.text()).toContain('否')
  expect(wrapper.text()).toContain('长春办事处')
})
it('keeps separate supplementary product and payment-plan tabs', async () => {
  wrapper = render()
  await flushPromises()
  for (const label of ['真实产品', '租赁产品', '产品配置', '回款计划', '装箱单'])
    expect(wrapper.text()).toContain(label)
})
it('retries a rejected native read', async () => {
  vi.mocked(getAssociatedData).mockRejectedValueOnce(new Error('DENIED'))
  wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('加载失败')
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('NATIVE-PRODUCT')
})
it('discards a late response after the project changes', async () => {
  let complete: (value: { list: (typeof row)[]; total: number }) => void = () => {}
  vi.mocked(getAssociatedData).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        complete = resolve
      })
  )
  wrapper = render()
  await wrapper.setProps({ projectId: '101' })
  await flushPromises()
  complete({
    list: [{ ...row, values: { ...row.values, productCode: 'STALE-PRODUCT' } }],
    total: 1
  })
  await flushPromises()
  expect(wrapper.text()).toContain('NATIVE-PRODUCT')
  expect(wrapper.text()).not.toContain('STALE-PRODUCT')
})

it('keeps page two while reading and resets the page for a different project', async () => {
  const first = Array.from({ length: 20 }, (_, index) => ({
    ...row,
    id: String(index + 1),
    values: { ...row.values, productCode: 'FIRST-' + index }
  }))
  const second = Array.from({ length: 18 }, (_, index) => ({
    ...row,
    id: String(index + 21),
    values: { ...row.values, productCode: 'SECOND-' + index }
  }))
  vi.mocked(getAssociatedData)
    .mockResolvedValueOnce({ list: first, total: 38 })
    .mockResolvedValueOnce({ list: second, total: 38 })
  wrapper = render()
  await flushPromises()
  const secondPage = wrapper.findAll('.el-pager li.number').find((item) => item.text() === '2')!
  await secondPage.trigger('click')
  await flushPromises()
  expect(getAssociatedData).toHaveBeenLastCalledWith('100', 'products', 2, 20)
  expect(wrapper.get('.el-pager li.is-active').text()).toBe('2')
  expect(wrapper.text()).toContain('SECOND-0')
  expect(wrapper.text()).not.toContain('FIRST-0')
  await wrapper.setProps({ projectId: '101' })
  await flushPromises()
  expect(getAssociatedData).toHaveBeenLastCalledWith('101', 'products', 1, 20)
  expect(wrapper.text()).toContain('NATIVE-PRODUCT')
})
it('keeps the second shipment page while the request is loading', async () => {
  const first = Array.from({ length: 20 }, (_, index) => ({ id: index + 1, deviceSn: 'FIRST-SN' }))
  const second = Array.from({ length: 18 }, (_, index) => ({ id: index + 21, deviceSn: 'SECOND-SN' }))
  vi.mocked(getDeviceShipments)
    .mockResolvedValue({ list: first, total: 38 })
    .mockResolvedValueOnce({ list: first, total: 38 })
    .mockResolvedValueOnce({ list: second, total: 38 })
  wrapper = mount(ShipmentPanel, {
    props: { deviceId: 100 },
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  const secondPage = wrapper.findAll('.el-pager li.number').find((item) => item.text() === '2')!
  await secondPage.trigger('click')
  await flushPromises()
  expect(getDeviceShipments).toHaveBeenLastCalledWith(100, { pageNo: 2, pageSize: 20 })
  expect(wrapper.get('.el-pager li.is-active').text()).toBe('2')
  expect(wrapper.text()).toContain('SECOND-SN')
  expect(wrapper.text()).not.toContain('FIRST-SN')
})
