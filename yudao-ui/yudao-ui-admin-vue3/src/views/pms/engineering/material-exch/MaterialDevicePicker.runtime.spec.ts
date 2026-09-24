import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MaterialDevicePicker from './MaterialDevicePicker.vue'
import { mount, passthrough, tableColumn, findByTestId } from '../../platform/dynamic-form/components/runtimeTestHarness'
import type { MaterialExchangeSerialVO } from '@/api/pms/engineering/material-exch'

const api = vi.hoisted(() => ({ getDeliveryScopePage: vi.fn() }))
vi.mock('@/api/pms/commerce', () => api)
const table = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, expose }) {
    expose({ toggleRowSelection: vi.fn() })
    return () => h('table', attrs, slots.default?.())
  }
})
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
/** 设备清单：交付范围明细拆分行 + 未拆分范围基行 */
const scope = (scopeId: number, details: Array<Record<string, any>>) => ({
  id: scopeId, orderNo: `SO-${scopeId}`, lineNo: '10', itemCode: `ITEM-${scopeId}`,
  allocatedQuantity: details.length ? undefined : 3, scopeStatus: 'ACTIVE', details
})
const detail = (id: number) => ({ id, productCode: `P-${id}`, deviceTypeCode: 'SWITCH', allocatedQuantity: 2, status: 'ACTIVE' })
const rows = (root: any): Array<Record<string, any>> =>
  findByTestId(root, 'device-candidates')!.props!.data as Array<Record<string, any>>

const setup = (initialProject?: number) => {
  const projectId = ref(initialProject)
  const serials = ref<MaterialExchangeSerialVO[]>([])
  const host = defineComponent({ setup: () => () => h(MaterialDevicePicker, {
    projectId: projectId.value, modelValue: serials.value,
    'onUpdate:modelValue': value => { serials.value = value }
  }) })
  const result = mount(host, {}, { ElTable: table, ElTableColumn: tableColumn, ElInput: passthrough, ElInputNumber: passthrough })
  return { ...result, projectId, serials }
}

describe('换货设备清单选择', () => {
  beforeEach(() => { api.getDeliveryScopePage.mockReset() })

  it('未选择项目时不发出全库查询', async () => {
    const { app } = setup()
    await flush()
    expect(api.getDeliveryScopePage).not.toHaveBeenCalled()
    app.unmount()
  })

  it('按同口径展平交付范围：明细拆分行带稳定引用，未拆分范围行作为基行', async () => {
    api.getDeliveryScopePage.mockImplementation(() => Promise.resolve({
      list: [scope(60, [detail(7), detail(8)]), scope(61, [])], total: 2
    }))
    const { root, app } = setup(10)
    await flush()
    expect(rows(root).map((row: any) => row.key)).toEqual(['D7', 'D8', 'S61'])
    expect(rows(root)[0]).toEqual(expect.objectContaining({
      scopeDetailId: 7, scopeId: 60, orderNo: 'SO-60', itemCode: 'ITEM-60', allocatedQuantity: 2
    }))
    expect(rows(root)[2]).toEqual(expect.objectContaining({
      scopeDetailId: undefined, scopeId: 61, allocatedQuantity: 3
    }))
    expect(api.getDeliveryScopePage).toHaveBeenLastCalledWith(expect.objectContaining({
      projectId: 10, includeHistory: false
    }))
    app.unmount()
  })

  it('勾选清单行携带引用与换货数量，翻页保留选择，取消全选只移除本页', async () => {
    const all = [scope(60, [detail(7), detail(8)]), scope(61, []), scope(62, [])]
    api.getDeliveryScopePage.mockImplementation(() => Promise.resolve({ list: all, total: all.length }))
    const { root, serials, app } = setup(10)
    await flush()
    const candidates = () => findByTestId(root, 'device-candidates')!.props!
    ;(candidates().onSelect as Function)([rows(root)[0], rows(root)[2]], rows(root)[0])
    await flush()
    expect(serials.value.map(row => row.scopeDetailId ?? row.scopeId)).toEqual([7, 61])
    expect(serials.value[0].quantity).toBe(1)
    const pagination = findByTestId(root, 'device-pagination')!.props!
    ;(pagination['onUpdate:limit'] as Function)(2)
    await (pagination.onPagination as Function)()
    await flush()
    // 翻页后第二页候选行为 D7/D8；取消全选只移除本页，S61 翻页保留
    ;(candidates().onSelectAll as Function)([])
    await flush()
    expect(serials.value.map(row => row.scopeDetailId ?? row.scopeId)).toEqual([61])
    app.unmount()
  })

  it('勾选清单行携带订单行快照且不伪造序列号', async () => {
    api.getDeliveryScopePage.mockImplementation(() => Promise.resolve({ list: [scope(60, [detail(7)])], total: 1 }))
    const { root, serials, app } = setup(10)
    await flush()
    ;(findByTestId(root, 'device-candidates')!.props!.onSelect as Function)([rows(root)[0]], rows(root)[0])
    await flush()
    const selectedRow = serials.value[0]
    expect(selectedRow.scopeDetailId).toBe(7)
    expect(selectedRow.scopeId).toBe(60)
    expect(selectedRow.orderNo).toBe('SO-60')
    expect(selectedRow.itemCode).toBe('ITEM-60')
    expect(selectedRow.sn).toBeUndefined()
    app.unmount()
  })

  it('加载失败保留已选清单行，候选列表为空', async () => {
    api.getDeliveryScopePage.mockResolvedValueOnce({ list: [scope(60, [detail(7)])], total: 1 })
    const { root, serials, app } = setup(10)
    await flush()
    ;(findByTestId(root, 'device-candidates')!.props!.onSelect as Function)([rows(root)[0]], rows(root)[0])
    await flush()
    api.getDeliveryScopePage.mockRejectedValueOnce(new Error('offline'))
    await (findByTestId(root, 'device-pagination')!.props!.onPagination as Function)()
    await flush()
    expect(serials.value[0].scopeDetailId).toBe(7)
    expect(rows(root)).toEqual([])
    app.unmount()
  })
})
