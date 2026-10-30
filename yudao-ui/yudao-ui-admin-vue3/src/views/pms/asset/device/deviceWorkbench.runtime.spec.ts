import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Devices from './index.vue'
import * as DeviceApi from '@/api/pms/asset/device'
import { mount, passthrough, tableColumn, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const detail = vi.hoisted(() => ({ mounted: vi.fn(), openDetail: vi.fn(), openStatusChange: vi.fn(), clearDetail: vi.fn() }))
vi.mock('./DeviceWorkbenchDialogs.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({ setup(_, { expose }) {
    detail.mounted()
    expose({ openDetail: detail.openDetail, openStatusChange: detail.openStatusChange, clearDetail: detail.clearDetail })
    return () => h('div', '设备详情')
  } }) }
})
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@vueuse/core', async () => { const { ref } = await import('vue'); return { useMediaQuery: () => ref(false) } })
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ delConfirm: vi.fn(), success: vi.fn() }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: {}, getStrDictOptions: () => [] }))
vi.mock('@/api/pms/asset/device', () => ({ getDevicePage: vi.fn() }))
vi.mock('@/api/pms/asset/device/archive', () => ({ deleteDeviceArchive: vi.fn() }))
const apps: Array<{ unmount: () => void }> = []
const flush = async () => { for (let i = 0; i < 10; i++) await nextTick() }
const render = async (props = {}) => {
  const view = mount(Devices, props, { ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough,
    ElTable: passthrough, ElTableColumn: tableColumn })
  apps.push(view.app)
  await flush()
  return { ...view, state: (view.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(DeviceApi.getDevicePage).mockResolvedValue({ list: [{ deviceId: 1, sn: 'SN1', projectName: '项目甲', customerName: '客户甲' }], total: 1 } as any)
})
afterEach(() => apps.splice(0).forEach(app => app.unmount()))

it('loads one list without mounting detail dialogs or offering device creation', async () => {
  const view = await render()
  expect(DeviceApi.getDevicePage).toHaveBeenCalledTimes(1)
  expect(detail.mounted).not.toHaveBeenCalled()
  expect(view.state.dialogsComponent).toBeUndefined()
  expect(textOf(view.root)).not.toContain('新增设备')
})

it('queries all three names and resets them while preserving the embedded project scope', async () => {
  const { state } = await render({ projectId: 42 })
  state.query.projectName = '项目甲'
  state.query.customerName = '客户甲'
  state.query.departmentName = '杭州'
  state.query.pageNo = 3
  state.handleQuery()
  await flush()
  expect(DeviceApi.getDevicePage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 42,
    projectName: '项目甲', customerName: '客户甲', departmentName: '杭州', pageNo: 1 }))
  state.resetQuery()
  await flush()
  expect(DeviceApi.getDevicePage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 42,
    projectName: undefined, customerName: undefined, departmentName: undefined, pageNo: 1 }))
})

it('mounts details on demand and opens the selected device', async () => {
  const { state } = await render()
  await state.openDetail({ deviceId: 1, sn: 'SN1' })
  expect(detail.mounted).toHaveBeenCalledTimes(1)
  expect(detail.openDetail).toHaveBeenCalledWith({ deviceId: 1, sn: 'SN1' })
})

it('preserves a Long project ID exactly instead of dropping the embedded project filter', async () => {
  const projectId = '2109733095671988226'
  const { state } = await render({ projectId })
  expect(DeviceApi.getDevicePage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId }))
  state.resetQuery()
  await flush()
  expect(DeviceApi.getDevicePage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId }))
})

it('does not let an earlier slow query overwrite the latest result or loading state', async () => {
  const { state } = await render()
  let resolveOld!: (value: any) => void
  vi.mocked(DeviceApi.getDevicePage).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const old = state.load()
  vi.mocked(DeviceApi.getDevicePage).mockResolvedValueOnce({ list: [{ deviceId: 2, sn: 'SN2' }], total: 2 } as any)
  await state.load()
  resolveOld({ list: [{ deviceId: 3, sn: 'SN3' }], total: 3 })
  await old
  expect(state.rows).toEqual([{ deviceId: 2, sn: 'SN2' }])
  expect(state.total).toBe(2)
  expect(state.loading).toBe(false)
})
