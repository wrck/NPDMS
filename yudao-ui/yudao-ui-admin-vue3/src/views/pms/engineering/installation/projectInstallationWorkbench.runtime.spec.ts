import { beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Installation from './ProjectInstallationWorkbench.vue'
import * as DeviceApi from '@/api/pms/asset/device'
import * as InstallationApi from '@/api/pms/engineering/installation'
import { mount, passthrough, tableColumn, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/components/PmsLocationSelector/index.vue', () => ({ default: defineComponent({ setup: () => () => h('location-selector') }) }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/pms/asset/device', () => ({ getDevicePage: vi.fn() }))
vi.mock('@/api/pms/project/projects', () => ({ __v_isRef: false, getProjectPage: vi.fn() }))
vi.mock('@/api/pms/asset/device/archive', () => ({ __v_isRef: false, getDeviceArchivePage: vi.fn() }))
vi.mock('@/api/pms/engineering/installation', () => ({ getInstallationPage: vi.fn(), getProjectInstallationAddressCompletion: vi.fn(), createInstallation: vi.fn(), createInstallationBatch: vi.fn(), getInstallation: vi.fn(), updateInstallation: vi.fn(), deleteInstallation: vi.fn() }))
vi.mock('@/api/system/user', () => ({ __v_isRef: false, getSimpleUserList: vi.fn().mockResolvedValue([{ id: 9, nickname: '当前用户' }]) }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 9 } }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ENG_STATUS: 'installation' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const deviceTableStub = defineComponent({ setup(_, { slots, attrs, expose }) { expose({ clearSelection: vi.fn() }); return () => h('table', attrs, slots.default?.()) } })
const imageStub = defineComponent({ setup(_, { attrs }) { return () => h('photo', attrs) } })
const nodes = (node: TestNode, type: string): TestNode[] => [...(node.type === type ? [node] : []), ...node.children.flatMap(child => nodes(child, type))]
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const render = () => {
  const mounted = mount(Installation, { projectId: 1 }, { ElTable: deviceTableStub, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough, PmsEntitySelect: passthrough, ProjectDeviceSelect: passthrough, DeviceTag: passthrough, UploadImg: imageStub })
  return { ...mounted, state: (mounted.vm as any).$.setupState }
}
const completed = { id: 8, projectId: 1, code: 'INS-COMPLETE', status: 2, version: 3, installLocation: '历史站点/机房A', installTime: 1788055200000, photoUrl: 'historical-photo' }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(InstallationApi.getProjectInstallationAddressCompletion).mockResolvedValue({ totalDevices: 0, addressedDevices: 0, completed: false })
  vi.mocked(InstallationApi.getInstallationPage).mockResolvedValue({ list: [], total: 0 })
  vi.mocked(DeviceApi.getDevicePage).mockResolvedValue({ list: [], total: 0 })
})


it('checks all project device pages independent of filtering', async () => {
  const first = Array.from({ length: 100 }, (_, i) => ({ deviceId: i + 1, sn: `SN${i + 1}`, locationSnapshot: '地址', syncStatus: 'FRESH' as const }))
  vi.mocked(DeviceApi.getDevicePage).mockImplementation(async q => q.pageNo === 1 ? { list: first, total: 101 } : { list: [{ deviceId: 101, sn: 'SN101', syncStatus: 'FRESH' as const }], total: 101 })
  vi.mocked(InstallationApi.getProjectInstallationAddressCompletion).mockResolvedValue({ totalDevices: 101, addressedDevices: 100, completed: false })
  const m = render()
  try {
    await flush(); await m.state.load()
    expect(m.state.devices).toHaveLength(101)
    expect(m.state.completed).toBe(false)
    m.state.sn = 'SN1'
    expect(m.state.completed).toBe(false)
    vi.mocked(InstallationApi.getInstallationPage).mockResolvedValue({ list: [{ id: 8, projectId: 1, equipmentId: 101, installLocation: '末台地址', status: 0 }], total: 1 })
    vi.mocked(InstallationApi.getProjectInstallationAddressCompletion).mockResolvedValue({ totalDevices: 101, addressedDevices: 101, completed: true })
    await m.state.load()
    expect(m.state.completed).toBe(true)
  } finally { m.app.unmount() }
})
it('submits one batch request and preserves the whole selection on failure', async () => {
  const m = render()
  try {
    await flush()
    m.state.openDevices([{ deviceId: 11, sn: 'SN11' }, { deviceId: 12, sn: 'SN12' }]); await flush()
    m.state.form.locationMaintenance = { fallbackLocation: '共同安装地址' }
    vi.mocked(InstallationApi.createInstallationBatch).mockRejectedValueOnce(new Error('failed')).mockResolvedValueOnce([101, 102])
    await m.state.save()
    expect(m.state.formVisible).toBe(true)
    expect(m.state.targetDevices.map(d => d.deviceId)).toEqual([11, 12])
    expect(InstallationApi.createInstallationBatch).toHaveBeenCalledTimes(1)
    expect(InstallationApi.createInstallationBatch).toHaveBeenLastCalledWith(expect.objectContaining({ equipmentIds: [11, 12], installLocation: '共同安装地址', locationMaintenance: undefined }))
    expect(InstallationApi.createInstallation).not.toHaveBeenCalled()
    await m.state.save()
    expect(m.state.formVisible).toBe(false)
    expect(InstallationApi.createInstallationBatch).toHaveBeenCalledTimes(2)
  } finally { m.app.unmount() }
})

it('sends current account rather than edited installer and omits manual time when saving', async () => {
  const m = render()
  try {
    await flush()
    m.state.openDevices([{ deviceId: 11, sn: 'SN11' }]); await flush()
    m.state.form.installerUserId = 999
    m.state.form.installTime = 946684800000
    m.state.form.locationMaintenance = { fallbackLocation: '安装地址' }
    vi.mocked(InstallationApi.createInstallationBatch).mockResolvedValueOnce([101])
    await m.state.save()
    const request = vi.mocked(InstallationApi.createInstallationBatch).mock.calls[0][0]
    expect(request.installerUserId).toBe(9)
    expect(request).not.toHaveProperty('installTime')
    expect(request).toEqual(expect.objectContaining({ equipmentIds: [11], installLocation: '安装地址' }))
  } finally { m.app.unmount() }
})
it('rejects empty and failed completion and keeps historical records immutable', async () => {
  const m = render()
  try {
    await flush(); await m.state.load()
    expect(m.state.completed).toBe(false)
    vi.mocked(DeviceApi.getDevicePage).mockRejectedValue(new Error('unavailable'))
    await m.state.load()
    expect(m.state.completed).toBe(false)
    expect(m.state.errorText).toBeTruthy()
    m.state.openForm(completed); await flush()
    expect(m.state.readOnly).toBe(true)
    await m.state.save()
    expect(InstallationApi.updateInstallation).not.toHaveBeenCalled()
    expect(InstallationApi.deleteInstallation).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})

it('normalizes new site address and removes an empty child location without mutating the form', async () => {
  const m = render()
  try {
    await flush()
    m.state.openDevices([{ deviceId: 13, sn: 'SN13' }])
    m.state.form.locationMaintenance = {
      address: { countryName: '中国', provinceCode: '110000', provinceName: '北京市', detailAddress: '验收街1号' },
      site: { name: '验收站点' }, siteLocation: { name: '', code: '' }, extraSiteLocations: []
    }
    const payload = m.state.savePayload()
    expect(payload.locationMaintenance.address.fullAddress).toBe('中国北京市验收街1号')
    expect(payload.locationMaintenance.siteLocation).toBeUndefined()
    expect(payload.installLocation).toBe('中国北京市验收街1号 / 验收站点')
    expect(m.state.form.locationMaintenance.siteLocation.name).toBe('')
    let error: Error | undefined
    m.state.form.locationMaintenance.address.detailAddress = ''
    m.state.rules.installLocation[0].validator({}, '', value => { error = value })
    expect(error?.message).toBe('请填写详细地址')
  } finally { m.app.unmount() }
})


it('shows device-scoped installation history and keeps every historical record read-only', async () => {
  const m = render()
  try {
    await flush()
    m.state.rows = [{ ...completed, equipmentId: 11 }, { id: 9, projectId: 1, equipmentId: 12, status: 0 }]
    m.state.openHistory({ deviceId: 11, sn: 'SN11' })
    expect(m.state.deviceHistory.map(row => row.id)).toEqual([8])
    expect(m.state.historyVisible).toBe(true)
    m.state.openForm({ id: 9, projectId: 1, equipmentId: 12, status: 0 })
    expect(m.state.readOnly).toBe(true)
    await m.state.save()
    expect(InstallationApi.updateInstallation).not.toHaveBeenCalled()
  } finally { m.app.unmount() }
})

it('prefills current manual and structured addresses without editing history or carrying another device address', async () => {
  const m = render()
  try {
    await flush()
    const manual = { deviceId: 11, sn: 'SN11' }
    const structured = { deviceId: 12, sn: 'SN12' }
    m.state.rows = [
      { id: 100, equipmentId: 11, status: 0, installLocation: '未生效地址' },
      { id: 99, equipmentId: 11, status: 2, effectiveFrom: '2026-10-08', effectiveTo: '2026-10-09', installLocation: '旧地址' },
      { id: 98, equipmentId: 11, status: 2, effectiveFrom: '2026-10-09', installLocation: '当前手工地址' },
      { id: 97, equipmentId: 12, status: 2, effectiveFrom: '2026-10-09', siteId: 21, siteVersion: 3, siteLocationId: 22, siteLocationVersion: 4, installLocation: '完整地址 / 站点 / 机房 / 机柜' }
    ]
    m.state.openDevices([manual])
    expect(m.state.form.locationMaintenance).toEqual({ projectId: 1, fallbackLocation: '当前手工地址' })
    expect(m.state.form.id).toBeUndefined()
    expect(m.state.readOnly).toBe(false)
    expect(m.state.form.installerUserId).toBe(9)
    m.state.openDevices([structured])
    expect(m.state.form.locationMaintenance).toEqual({ projectId: 1, site: { id: 21, expectedVersion: 3 }, siteLocation: { id: 22, expectedVersion: 4 }, fallbackLocation: '完整地址 / 站点 / 机房 / 机柜' })
    expect(m.state.form.id).toBeUndefined()
    m.state.openDevices([manual, structured])
    expect(m.state.form.locationMaintenance.fallbackLocation).toBeUndefined()
    m.state.openDevices([{ deviceId: 13, sn: 'SN13' }])
    expect(m.state.form.locationMaintenance.fallbackLocation).toBeUndefined()
    expect(m.state.rows[0].installLocation).toBe('未生效地址')
  } finally { m.app.unmount() }
})
