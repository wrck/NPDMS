import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Panel from './ProjectSerialPanel.vue'
import * as DeviceApi from '@/api/pms/asset/device'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/asset/device', () => ({ getDevicePage: vi.fn(), getDevice: vi.fn(), getWarrantyRecords: vi.fn() }))
const flush = async () => { for (let i = 0; i < 12; i++) await nextTick() }
const render = () => {
  const mounted = mount(Panel, { projectId: 1002, active: true }, { ElTable: passthrough, ElTableColumn: tableColumn, ElInput: passthrough })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(DeviceApi.getDevicePage).mockResolvedValue({ list: [{ deviceId: 11, sn: 'SN11', locationSnapshot: undefined }], total: 1 })
  vi.mocked(DeviceApi.getDevice).mockResolvedValue({ networkVersion: { syncStatus: 'NOT_AVAILABLE' } } as any)
  vi.mocked(DeviceApi.getWarrantyRecords).mockResolvedValue({ records: { list: [], total: 0 } })
})
it('applies combined server filters from page one and clears them on reset', async () => {
  const m = render()
  try {
    await flush()
    m.state.sn = ' SN11 '; m.state.deviceName = ' 核心设备 '; m.state.productCode = ' P001 '
    m.state.pageNo = 3; m.state.search(); await flush()
    expect(DeviceApi.getDevicePage).toHaveBeenLastCalledWith({
      projectId: 1002, sn: 'SN11', name: '核心设备', productCode: 'P001', pageNo: 1, pageSize: 20
    })
    m.state.resetSearch(); await flush()
    expect(DeviceApi.getDevicePage).toHaveBeenLastCalledWith({
      projectId: 1002, sn: undefined, name: undefined, productCode: undefined, pageNo: 1, pageSize: 20
    })
  } finally { m.app.unmount() }
})
it('uses the projected complete address and refreshes it when returning to the serial tab', async () => {
  vi.mocked(DeviceApi.getDevicePage).mockResolvedValue({ list: [{ deviceId: 11, sn: 'SN11', locationSnapshot: JSON.stringify({ fallbackLocation: '当前手工地址' }) }], total: 1 })
  const m = render()
  try {
    await flush()
    expect(m.state.rows[0].location).toBe('当前手工地址')
    vi.mocked(DeviceApi.getDevicePage).mockResolvedValue({ list: [{ deviceId: 11, sn: 'SN11', locationSnapshot: JSON.stringify({ fallbackLocation: '地址 / 站点 / 机房 / 机柜', site: { id: 21 } }) }], total: 1 })
    const props = (m.vm as any).$.props
    props.active = false; await flush(); props.active = true; await flush()
    expect(m.state.rows[0].location).toBe('地址 / 站点 / 机房 / 机柜')
  } finally { m.app.unmount() }
})
it('keeps readable legacy snapshot fallback without showing JSON', async () => {
  vi.mocked(DeviceApi.getDevicePage).mockResolvedValue({ list: [{ deviceId: 11, sn: 'SN11', locationSnapshot: JSON.stringify({ fallbackLocation: '地址 / 站点' }) }], total: 1 })
  const m = render()
  try { await flush(); expect(m.state.rows[0].location).toBe('地址 / 站点') } finally { m.app.unmount() }
})
it('keeps the total while loading another page so pagination does not reset to page one', async () => {
  vi.mocked(DeviceApi.getDevicePage).mockResolvedValue({ list: [{ deviceId: 11, sn: 'SN11' }], total: 21 })
  const m = render()
  try {
    await flush()
    expect(m.state.total).toBe(21)
    let resolvePage!: (page: any) => void
    vi.mocked(DeviceApi.getDevicePage).mockImplementationOnce(() => new Promise(resolve => { resolvePage = resolve }))
    m.state.pageNo = 2
    const loading = m.state.load()
    await flush()
    expect(m.state.total).toBe(21)
    expect(m.state.pageNo).toBe(2)
    expect(DeviceApi.getDevicePage).toHaveBeenLastCalledWith({ projectId: 1002, sn: undefined, pageNo: 2, pageSize: 20 })
    resolvePage({ list: [{ deviceId: 21, sn: 'SN21' }], total: 21 })
    await loading
    expect(m.state.pageNo).toBe(2)
    expect(m.state.rows[0].sn).toBe('SN21')
  } finally { m.app.unmount() }
})


it('loads 40 devices and all columns through one page request', async () => {
  const rows = Array.from({ length: 40 }, (_, i) => ({ deviceId: i + 1, sn: `SN${i + 1}`, locationSnapshot: JSON.stringify({ fallbackLocation: '完整安装地址' }), bootVersion: 'BOOT', cpldVersion: 'CPLD', warrantyMonths: 12, extendedCount: 3 }))
  vi.mocked(DeviceApi.getDevicePage).mockResolvedValue({ list: rows, total: 40 })
  const m = render()
  try {
    await flush()
    expect(m.state.rows).toHaveLength(40)
    expect(DeviceApi.getDevicePage).toHaveBeenCalledTimes(1)
    expect(DeviceApi.getDevice).not.toHaveBeenCalled()
    expect(DeviceApi.getWarrantyRecords).not.toHaveBeenCalled()
    expect(m.state.rows[0]).toMatchObject({ location: '完整安装地址', bootVersion: 'BOOT', cpldVersion: 'CPLD', warrantyMonths: 12, extendedCount: 3 })
  } finally { m.app.unmount() }
})
