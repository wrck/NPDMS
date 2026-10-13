// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import Panel from './DeviceWarrantyRenewalTable.vue'
import Pagination from '@/components/Pagination/index.vue'
import { getWarrantyRenewals } from '@/api/pms/asset/warranty-renewal'

vi.mock('@/api/pms/asset/warranty-renewal', () => ({ getWarrantyRenewals: vi.fn() }))
vi.mock('@/store/modules/app', () => ({ useAppStore: () => ({ currentSize: 'default' }) }))
let wrapper: ReturnType<typeof mount> | undefined
const render = (realPagination = false) =>
  mount(Panel, {
    props: { projectId: 55 },
    global: {
      plugins: [ElementPlus],
      components: { Pagination },
      stubs: { Pagination: !realPagination }
    }
  })
beforeEach(() => {
  vi.mocked(getWarrantyRenewals).mockReset().mockResolvedValue({ list: [], total: 0 })
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

it('shows complete renewal fields including zero months and unknown latest marker without a device id', async () => {
  vi.mocked(getWarrantyRenewals).mockResolvedValue({
    total: 1,
    list: [
      {
        id: 1,
        deviceSn: 'NO-MASTER-SN',
        warrantyContractNo: 'RENEW-01',
        warrantyGrade: '53030001',
        warrantyStartDate: '2020-01-01',
        warrantyEndDate: '2022-04-17',
        warrantyMonths: 0,
        extended: true,
        isLatest: null,
        remark: '原备注'
      }
    ]
  })
  wrapper = render()
  await flushPromises()
  expect(getWarrantyRenewals).toHaveBeenCalledWith({
    projectId: 55,
    deviceId: undefined,
    sn: undefined,
    pageNo: 1,
    pageSize: 20
  })
  const text = wrapper.get('[data-testid="warranty-renewal-table"]').text()
  for (const value of [
    'NO-MASTER-SN',
    'RENEW-01',
    '53030001',
    '2020-01-01',
    '2022-04-17',
    '未记录',
    '原备注'
  ])
    expect(text).toContain(value)
  expect(wrapper.findAll('tbody tr')[0].findAll('td')[5].text()).toBe('0')
})
it('reports a request failure and supports a successful retry', async () => {
  vi.mocked(getWarrantyRenewals).mockRejectedValueOnce(new Error('unavailable'))
  wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('续保记录加载失败')
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).not.toContain('续保记录加载失败')
  expect(wrapper.text()).toContain('暂无符合条件的续保记录')
})
it('does not show an old project response after the user changes projects', async () => {
  let resolveOld!: (result: { list: any[]; total: number }) => void
  vi.mocked(getWarrantyRenewals).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        resolveOld = resolve
      })
  )
  wrapper = render()
  await wrapper.setProps({ projectId: 56 })
  await flushPromises()
  resolveOld({ list: [{ id: 2, deviceSn: 'OLD-PROJECT' }], total: 1 })
  await flushPromises()
  expect(wrapper.text()).not.toContain('OLD-PROJECT')
})

it('keeps the selected page while the next renewal page is loading', async () => {
  let resolvePage!: (result: { list: any[]; total: number }) => void
  vi.mocked(getWarrantyRenewals).mockImplementation(({ pageNo }) =>
    pageNo === 2
      ? new Promise((resolve) => {
          resolvePage = resolve
        })
      : Promise.resolve({ total: 90, list: [{ id: pageNo, deviceSn: `PAGE-${pageNo}` }] })
  )
  wrapper = render(true)
  await flushPromises()
  await wrapper.get('.btn-next').trigger('click')
  await flushPromises()
  expect(getWarrantyRenewals).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 2 }))
  expect(wrapper.get('.el-pager .is-active').text()).toBe('2')
  resolvePage({ total: 90, list: [{ id: 2, deviceSn: 'PAGE-2' }] })
  await flushPromises()
  expect(wrapper.text()).toContain('PAGE-2')
  await wrapper.get('.btn-next').trigger('click')
  await flushPromises()
  expect(getWarrantyRenewals).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 3 }))
  expect(wrapper.get('.el-pager .is-active').text()).toBe('3')
})
