// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import SoftwarePanel from './DeviceSoftwareSourceHistoryPanel.vue'
import FactoryPanel from './DeviceFactorySourceHistoryPanel.vue'
import InstallationPanel from './DeviceInstallationSourceHistoryPanel.vue'
import { getSoftwareVersionHistory, getDeviceSourceFactoryVersions } from '@/api/pms/asset/device'
import { getInstallationSourceRecords } from '@/api/pms/engineering/installation-source-history'
vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/api/pms/asset/device', () => ({
  getSoftwareVersionHistory: vi.fn(),
  getDeviceSourceFactoryVersions: vi.fn()
}))
vi.mock('@/api/pms/engineering/installation-source-history', () => ({
  getInstallationSourceRecords: vi.fn()
}))
const softwareRow = {
  id: '900000000000000001',
  projectId: '100',
  sourceProjectKey: '10',
  sourceSystem: 'DPPMS',
  sourceKey: '301',
  sourceChecksum: 'checksum',
  values: {
    sourceId: '301',
    sourceProjectId: '10',
    barCode: 'source-sn',
    conp: 'SOURCE-CONP',
    bootBak: 'BOOT-OLD',
    conpChange: 0,
    bootChange: 0,
    datastate: 0
  }
}
const softwareHistoryRow = {
  record: {
    id: 9001,
    projectId: 100,
    dataSource: 'DPPMS',
    deviceSn: 'source-sn',
    conpVersion: 'SOURCE-CONP',
    bootVersion: 'BOOT',
    dataState: 0
  },
  previous: null,
  legacyAttributes: { boot_bak: 'BOOT-OLD', conp_change: 0, boot_change: 0 }
}
let wrapper: ReturnType<typeof mount> | undefined
beforeEach(() => {
  vi.mocked(getSoftwareVersionHistory)
    .mockReset()
    .mockResolvedValue({ list: [softwareHistoryRow], total: 1 })
  vi.mocked(getDeviceSourceFactoryVersions)
    .mockReset()
    .mockResolvedValue({
      list: [{ ...softwareRow, values: { deviceSn: 'source-sn', conpVersion: 'FACTORY-CONP' } }],
      total: 1
    })
  vi.mocked(getInstallationSourceRecords)
    .mockReset()
    .mockResolvedValue({
      list: [
        {
          id: '900000000000000002',
          projectId: '100',
          equipmentId: '500',
          sourceRecordKey: '302',
          sourceId: '302',
          sourceProjectId: '10',
          sourceDeviceSn: 'source-sn',
          installLocation: '来源安装地址',
          locationResolutionStatus: 'UNRESOLVED',
          values: { sourceCreatedAt: '2023-06-18 13:57:29', sourceUpdatedBy: null },
          historicalInstallation: {
            id: '900000000000000002',
            projectId: '100',
            equipmentId: '500',
            code: 'DPPMS-302',
            status: 2,
            addressVersion: 0,
            effectiveFrom: '2023-06-18 13:57:29',
            currentEquipmentId: '500'
          }
        }
      ],
      total: 1
    })
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})
const renderSoftware = () =>
  mount(SoftwarePanel, { props: { deviceId: 500 }, global: { plugins: [ElementPlus] } })
it('reads formal history and preserves zero legacy flags and original backups in the reused drawer', async () => {
  wrapper = renderSoftware()
  await flushPromises()
  expect(getSoftwareVersionHistory).toHaveBeenCalledWith(500, { pageNo: 1, pageSize: 20 })
  expect(wrapper.text()).toContain('SOURCE-CONP')
  await wrapper.get('button.el-button--primary').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('BOOT原备份')
  const inputValues = wrapper
    .findAll('input')
    .map((input) => (input.element as HTMLInputElement).value)
  expect(inputValues).toContain('BOOT-OLD')
  expect(inputValues).toContain('0')
})
it('never shows a late source response from the previous device', async () => {
  let finish: (page: { list: (typeof softwareHistoryRow)[]; total: number }) => void = () => {}
  vi.mocked(getSoftwareVersionHistory).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve
      })
  )
  wrapper = renderSoftware()
  await wrapper.setProps({ deviceId: 501 })
  await flushPromises()
  finish({
    list: [
      {
        ...softwareHistoryRow,
        record: { ...softwareHistoryRow.record, conpVersion: 'STALE-DEVICE' }
      }
    ],
    total: 1
  })
  await flushPromises()
  expect(wrapper.text()).toContain('SOURCE-CONP')
  expect(wrapper.text()).not.toContain('STALE-DEVICE')
})
it('retains the second page total while loading and resets paging when the device changes', async () => {
  vi.mocked(getSoftwareVersionHistory)
    .mockResolvedValueOnce({ list: [softwareHistoryRow], total: 21 })
    .mockResolvedValueOnce({
      list: [
        {
          ...softwareHistoryRow,
          record: { ...softwareHistoryRow.record, conpVersion: 'SECOND-PAGE' }
        }
      ],
      total: 21
    })
  wrapper = renderSoftware()
  await flushPromises()
  await wrapper
    .findAll('.el-pager li.number')
    .find((item) => item.text() === '2')!
    .trigger('click')
  await flushPromises()
  expect(getSoftwareVersionHistory).toHaveBeenLastCalledWith(500, {
    pageNo: 2,
    pageSize: 20
  })
  expect(wrapper.text()).toContain('SECOND-PAGE')
  expect(wrapper.get('.el-pager li.is-active').text()).toBe('2')
  await wrapper.setProps({ deviceId: 501 })
  await flushPromises()
  expect(getSoftwareVersionHistory).toHaveBeenLastCalledWith(501, {
    pageNo: 1,
    pageSize: 20
  })
})
it('retries failed business reads without displaying stale records', async () => {
  vi.mocked(getSoftwareVersionHistory).mockRejectedValueOnce(new Error('DENIED'))
  wrapper = renderSoftware()
  await flushPromises()
  expect(wrapper.text()).toContain('加载失败')
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('SOURCE-CONP')
})
it('reads factory facts through the dedicated business endpoint', async () => {
  wrapper = mount(FactoryPanel, { props: { deviceId: 500 }, global: { plugins: [ElementPlus] } })
  await flushPromises()
  expect(getDeviceSourceFactoryVersions).toHaveBeenCalledWith(500, { pageNo: 1, pageSize: 20 })
  expect(wrapper.text()).toContain('FACTORY-CONP')
})
it('reads adjudicated completed installations and preserves a null update actor', async () => {
  wrapper = mount(InstallationPanel, {
    props: { deviceId: 500 },
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  expect(getInstallationSourceRecords).toHaveBeenCalledWith({
    equipmentId: 500,
    pageNo: 1,
    pageSize: 20
  })
  expect(wrapper.text()).toContain('来源安装地址')
  expect(wrapper.text()).toContain('已安装')
  expect(wrapper.text()).toContain('当前安装')
  await wrapper.get('button.el-button--primary').trigger('click')
  await flushPromises()
  const inputValues = wrapper
    .findAll('input')
    .map((input) => (input.element as HTMLInputElement).value)
  expect(inputValues).toContain('DPPMS-302')
  expect(inputValues).toContain('2023-06-18 13:57:29.000')
  const updateActor = wrapper.findAll('.el-form-item').find((item) => item.text() === '更新人')!
  expect((updateActor.get('input').element as HTMLInputElement).value).toBe('—')
})

it('does not claim installation completion when the formal installation is missing', async () => {
  vi.mocked(getInstallationSourceRecords).mockResolvedValueOnce({
    list: [
      {
        id: '10',
        projectId: '100',
        equipmentId: '500',
        sourceRecordKey: '302',
        locationResolutionStatus: 'UNRESOLVED',
        values: {}
      }
    ],
    total: 1
  })
  wrapper = mount(InstallationPanel, {
    props: { deviceId: 500 },
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  expect(wrapper.text()).toContain('安装记录未关联')
  expect(wrapper.find('[data-testid="historical-installation-summary"]').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('已安装')
  expect(wrapper.text()).not.toContain('当前安装')
})
it('shows the ended installation as history with both real boundary dates', async () => {
  vi.mocked(getInstallationSourceRecords).mockResolvedValueOnce({
    list: [
      {
        id: '900000000000000002',
        projectId: '100',
        equipmentId: '500',
        sourceRecordKey: '302',
        sourceId: '302',
        installLocation: '正式历史地址',
        locationResolutionStatus: 'UNRESOLVED',
        values: {},
        historicalInstallation: {
          id: '900000000000000003',
          projectId: '100',
          equipmentId: '500',
          code: 'DPPMS-302',
          sourceRecordKey: '302',
          sourceHistory: true,
          status: 2,
          installLocation: '正式历史地址',
          addressId: '800',
          addressVersion: 0,
          effectiveFrom: '2025-03-24 06:32:08',
          effectiveTo: '2026-10-09 21:34:03'
        }
      }
    ],
    total: 1
  })
  wrapper = mount(InstallationPanel, {
    props: { deviceId: 500 },
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  const canonical = wrapper.get('[data-testid="historical-installation-summary"]')
  expect(canonical.attributes('data-installation-id')).toBe('900000000000000003')
  expect(wrapper.text()).toContain('正式历史地址')
  expect(canonical.get('[data-testid="historical-installation-status"]').text()).toBe('已安装')
  expect(canonical.get('[data-testid="historical-installation-current"]').text()).toBe('历史安装')
  expect(canonical.text()).toContain('2025-03-24 06:32:08.000')
  expect(canonical.text()).toContain('2026-10-09 21:34:03.000')
  expect(canonical.text()).not.toContain('当前安装')
})
it('does not conceal a canonical current pointer or effective time behind a historical label', async () => {
  vi.mocked(getInstallationSourceRecords).mockResolvedValueOnce({
    list: [
      {
        id: '10',
        projectId: '100',
        equipmentId: '500',
        sourceRecordKey: '302',
        sourceId: '302',
        locationResolutionStatus: 'UNRESOLVED',
        values: {},
        historicalInstallation: {
          id: '20',
          projectId: '100',
          equipmentId: '500',
          code: 'DPPMS-302',
          sourceRecordKey: '302',
          sourceHistory: true,
          status: 2,
          currentEquipmentId: '500',
          effectiveFrom: '2024-01-02 03:04:00'
        }
      }
    ],
    total: 1
  })
  wrapper = mount(InstallationPanel, {
    props: { deviceId: 500 },
    global: { plugins: [ElementPlus] }
  })
  await flushPromises()
  expect(wrapper.get('[data-testid="historical-installation-current"]').text()).toBe('当前安装')
  expect(wrapper.get('[data-testid="historical-installation-effective-time"]').text()).toContain(
    '2024-01-02 03:04:00'
  )
  expect(wrapper.get('[data-testid="historical-installation-status"]').text()).toBe('已安装')
})
