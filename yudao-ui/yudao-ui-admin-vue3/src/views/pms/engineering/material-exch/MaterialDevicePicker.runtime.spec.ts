import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MaterialDevicePicker from './MaterialDevicePicker.vue'
import { mount, passthrough, tableColumn, findByTestId } from '../../platform/dynamic-form/components/runtimeTestHarness'
import type { MaterialExchangeSerialVO } from '@/api/pms/engineering/material-exch'

const api = vi.hoisted(() => ({ getDeviceArchivePage: vi.fn() }))
vi.mock('@/api/pms/asset/device/archive', () => api)
const table = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, expose }) {
    expose({ toggleRowSelection: vi.fn() })
    return () => h('table', attrs, slots.default?.())
  }
})
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
const device = (id: number) => ({ id, sn: `SN-${id}`, name: `设备${id}`, productModel: 'MODEL', contractNo: 'C-1' })

const setup = (initialProject?: number) => {
  const projectId = ref(initialProject)
  const serials = ref<MaterialExchangeSerialVO[]>([])
  const host = defineComponent({ setup: () => () => h(MaterialDevicePicker, {
    projectId: projectId.value, modelValue: serials.value,
    'onUpdate:modelValue': value => { serials.value = value }
  }) })
  const result = mount(host, {}, { ElTable: table, ElTableColumn: tableColumn, ElInput: passthrough })
  return { ...result, projectId, serials }
}

describe('换货设备选择', () => {
  beforeEach(() => { api.getDeviceArchivePage.mockReset() })

  it('未选择项目时不发出全库查询', async () => {
    const { app } = setup()
    await flush()
    expect(api.getDeviceArchivePage).not.toHaveBeenCalled()
    app.unmount()
  })

  it('跨页多选保留前页设备，当前页取消全选只移除本页', async () => {
    api.getDeviceArchivePage.mockImplementation(({ pageNo }) => Promise.resolve({
      list: pageNo === 1 ? [device(1)] : [device(2)], total: 40
    }))
    const { root, serials, app } = setup(10)
    await flush()
    const candidates = () => findByTestId(root, 'device-candidates')!.props!
    ;(candidates().onSelect as Function)([device(1)], device(1))
    await flush()
    const pagination = findByTestId(root, 'device-pagination')!.props!
    ;(pagination['onUpdate:page'] as Function)(2)
    await (pagination.onPagination as Function)()
    await flush()
    ;(candidates().onSelectAll as Function)([device(2)])
    await flush()
    expect(serials.value.map(row => row.equipmentId)).toEqual([1, 2])
    ;(candidates().onSelectAll as Function)([])
    await flush()
    expect(serials.value.map(row => row.equipmentId)).toEqual([1])
    expect(api.getDeviceArchivePage).toHaveBeenLastCalledWith(expect.objectContaining({ selectionProjectId: 10, pageNo: 2 }))
    app.unmount()
  })

  it('翻页请求期间保留总数，避免分页组件重置到第一页', async () => {
    api.getDeviceArchivePage.mockResolvedValueOnce({ list: [device(1)], total: 40 })
    const { root, app } = setup(10)
    await flush()
    let resolvePage!: (value: unknown) => void
    api.getDeviceArchivePage.mockImplementationOnce(() => new Promise(resolve => { resolvePage = resolve }))
    const pagination = findByTestId(root, 'device-pagination')!.props!
    ;(pagination['onUpdate:page'] as Function)(2)
    const pending = (pagination.onPagination as Function)()
    await flush()
    expect(findByTestId(root, 'device-pagination')!.props!.total).toBe(40)
    expect(findByTestId(root, 'device-pagination')!.props!.page).toBe(2)
    resolvePage({ list: [device(2)], total: 40 })
    await pending
    app.unmount()
  })

  it('切换项目后旧请求不能回填候选设备', async () => {
    let resolveOld!: (value: unknown) => void
    api.getDeviceArchivePage.mockImplementation(({ selectionProjectId }) => selectionProjectId === 10
      ? new Promise(resolve => { resolveOld = resolve })
      : Promise.resolve({ list: [device(2)], total: 1 }))
    const { root, projectId, app } = setup(10)
    projectId.value = 20
    await flush()
    resolveOld({ list: [device(1)], total: 1 })
    await flush()
    expect(findByTestId(root, 'device-candidates')!.props!.data).toEqual([device(2)])
    app.unmount()
  })

  it('加载失败保留已选设备，候选列表为空', async () => {
    api.getDeviceArchivePage.mockResolvedValueOnce({ list: [device(1)], total: 1 })
    const { root, serials, app } = setup(10)
    await flush()
    ;(findByTestId(root, 'device-candidates')!.props!.onSelect as Function)([device(1)], device(1))
    await flush()
    api.getDeviceArchivePage.mockRejectedValueOnce(new Error('offline'))
    await (findByTestId(root, 'device-pagination')!.props!.onPagination as Function)()
    await flush()
    expect(serials.value[0].sn).toBe('SN-1')
    expect(findByTestId(root, 'device-candidates')!.props!.data).toEqual([])
    app.unmount()
  })
})
