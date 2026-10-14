import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MaterialDevicePicker from './MaterialDevicePicker.vue'
import { mount, passthrough, tableColumn, findByTestId } from '../../platform/dynamic-form/components/runtimeTestHarness'
import type { MaterialExchangeSerialVO } from '@/api/pms/engineering/material-exch'

const api = vi.hoisted(() => ({ getDeliveryScopePage: vi.fn() }))
vi.mock('@/api/pms/commerce', () => api)
// 换货产品下拉的 axios 导入链会在导入期访问 window，统一按 API 模块边界 mock
vi.mock('@/api/pms/asset/product-official', () => ({
  AssetProductOfficialApi: { page: vi.fn(async () => ({ total: 0, list: [] })) }
}))
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

const setup = (initialProject?: number | string) => {
  const projectId = ref(initialProject)
  const serials = ref<MaterialExchangeSerialVO[]>([])
  const pickerRef = ref<any>()
  const host = defineComponent({ setup: () => () => h(MaterialDevicePicker, {
    ref: pickerRef,
    projectId: projectId.value, modelValue: serials.value,
    'onUpdate:modelValue': value => { serials.value = value }
  }) })
  const result = mount(host, {}, { ElTable: table, ElTableColumn: tableColumn, ElInput: passthrough, ElInputNumber: passthrough })
  return { ...result, projectId, serials, pickerRef }
}

describe('换货设备清单选择', () => {
  beforeEach(() => { api.getDeliveryScopePage.mockReset() })

  it('长ID子项目保留完整ID，并读取全部范围页后才能选择物料', async () => {
    const id = '2109733194783391746'
    api.getDeliveryScopePage.mockResolvedValueOnce({ list: Array.from({ length: 200 }, (_, i) => scope(i + 1, [])), total: 201 })
      .mockResolvedValueOnce({ list: [scope(201, [])], total: 201 })
    const { app, pickerRef } = setup(id)
    try {
      await flush(); await flush()
      expect(api.getDeliveryScopePage.mock.calls.map(call => call[0])).toEqual([
        { projectId: id, pageNo: 1, pageSize: 200, includeHistory: false },
        { projectId: id, pageNo: 2, pageSize: 200, includeHistory: false }
      ])
      expect(pickerRef.value.$.setupState.groupRows).toHaveLength(201)
    } finally { app.unmount() }
  })

  it('后续范围页失败时不展示首批物料，保留既有选择', async () => {
    api.getDeliveryScopePage.mockResolvedValueOnce({ list: [scope(60, [])], total: 2 })
      .mockRejectedValueOnce(new Error('第二页不可读'))
    const result = mount(MaterialDevicePicker, { projectId: '2109733194783391746',
      modelValue: [{ scopeId: 80, quantity: 1 }] }, { ElTable: table, ElTableColumn: tableColumn })
    try {
      await flush(); await flush()
      const state = (result.vm as any).$.setupState
      expect(state.rows).toEqual([])
      expect(state.error).toContain('加载失败')
      expect(state.selectedRows[0].scopeId).toBe(80)
    } finally { result.app.unmount() }
  })

  it('空页尚未达到声明总数时拒绝把部分范围当作完整清单', async () => {
    api.getDeliveryScopePage.mockResolvedValueOnce({ list: [scope(60, [])], total: 2 })
      .mockResolvedValueOnce({ list: [], total: 2 })
    const { app, pickerRef } = setup(10)
    try {
      await flush(); await flush()
      expect(pickerRef.value.$.setupState.rows).toEqual([])
      expect(pickerRef.value.$.setupState.error).toContain('加载失败')
    } finally { app.unmount() }
  })

  it('同合同同产品合并，跨合同保持独立，按订单号和数字行号分配且拒绝超量', async () => {
    api.getDeliveryScopePage.mockResolvedValue({ list: [
      { id: 90, contractNo: 'C1', orderNo: 'SO-2', lineNo: '1', productCode: 'P1', allocatedQuantity: 4 },
      { id: 80, contractNo: 'C1', orderNo: 'SO-1', lineNo: '10', productCode: 'P1', allocatedQuantity: 2 },
      { id: 70, contractNo: 'C1', orderNo: 'SO-1', lineNo: '2', productCode: 'P1', allocatedQuantity: 1 },
      { id: 60, contractNo: 'C2', orderNo: 'SO-1', lineNo: '1', productCode: 'P1', allocatedQuantity: 8 }
    ], total: 4 })
    const { root, serials, app, pickerRef } = setup(10)
    try {
      await flush()
      const groups = rows(root)
      expect(groups).toHaveLength(2)
      expect(groups[0]).toEqual(expect.objectContaining({ contractNo: 'C1', itemCode: 'P1', orderNo: 'SO-1、SO-2', allocatedQuantity: 7 }))
      const picker = (findByTestId(root, 'device-candidates')!.props!.onSelect as Function)
      picker([groups[0]], groups[0]); await flush()
      expect(serials.value.map(row => row.scopeId)).toEqual([70])
      expect(serials.value[0].contractNo).toBe('C1')
      const state = pickerRef.value.$.setupState
      state.setQuantity(groups[0].key, 5); await flush()
      expect(serials.value.map(row => [row.scopeId, row.quantity])).toEqual([[70, 1], [80, 2], [90, 2]])
      expect(state.selectedRows).toHaveLength(1)
      state.setExchangeProduct(groups[0].key, 123); await flush()
      expect(serials.value.every(row => row.productId === 123)).toBe(true)
      state.setQuantity(groups[0].key, 8); await flush()
      expect(serials.value.reduce((sum, row) => sum + row.quantity!, 0)).toBe(5)
      expect(state.error).toContain('不能超过项目数量')
      state.remove(groups[0].key); await flush()
      expect(serials.value).toEqual([])
    } finally { app.unmount() }
  })

  it('未选择项目时不发出全库查询', async () => {
    const { app } = setup()
    await flush()
    expect(api.getDeliveryScopePage).not.toHaveBeenCalled()
    app.unmount()
  })

  it('拒绝已丢失精度的数字、非法字符串及超出Long范围的ID，不放宽为空项目查询', async () => {
    for (const id of [0, Number('2109733194783391746'), '0', '-1', '9223372036854775808']) {
      const { app, pickerRef } = setup(id)
      try {
        await flush()
        expect(pickerRef.value.$.setupState.validProject).toBe(false)
        expect(api.getDeliveryScopePage).not.toHaveBeenCalled()
      } finally { app.unmount() }
    }
  })

  it('消费当前交付范围接口的productCode/productDesc，保存稳定引用与真实物料编码', async () => {
    api.getDeliveryScopePage.mockResolvedValue({ list: [{ id: 60, orderNo: 'SO-60', lineNo: '1', contractNo: 'CONTRACT-60', productCode: '02051131', productDesc: '防火墙主机', allocatedQuantity: 1, scopeStatus: 'ACTIVE', details: [] }], total: 1 })
    const { root, serials, app } = setup(10)
    try {
      await flush()
      const row=rows(root)[0]
      expect(row).toEqual(expect.objectContaining({ scopeId: 60, contractNo: 'CONTRACT-60', itemCode: '02051131', productCode: '02051131', name: '防火墙主机' }))
      const candidates=findByTestId(root, 'device-candidates')!.props!
      ;(candidates.onSelect as Function)([row], row)
      await flush()
      expect(serials.value[0]).toEqual(expect.objectContaining({ scopeId: 60, quantity: 1, itemCode: '02051131', productCode: '02051131', productName: '防火墙主机' }))
    } finally { app.unmount() }
  })

  it('订单产品名称和合同来自所选范围，不能使用换货目标产品快照冒充原订单产品', async () => {
    api.getDeliveryScopePage.mockResolvedValue({ list: [{ id: 60, orderNo: 'SO-60', productCode: 'ORIGINAL', productDesc: '原订单防火墙型号', contractNo: 'C-60', details: [{ id: 7, productName: '设备分配名称', allocatedQuantity: 1 }] }], total: 1 })
    const result=mount(MaterialDevicePicker, { projectId: 10, modelValue: [{ scopeId: 60, scopeDetailId: 7, productName: '换货目标型号', quantity: 1 }] }, { ElTable: table, ElTableColumn: tableColumn, ElInputNumber: passthrough })
    try {
      await flush()
      const state=(result.vm as any).$.setupState
      expect(state.selectedRows[0]).toEqual(expect.objectContaining({
        name: '原订单防火墙型号', contractNo: 'C-60', allocatedQuantity: 1
      }))
    } finally { result.app.unmount() }
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
    // el-table 逐行勾选触发 select：先勾明细拆分行 D7，再勾未拆分范围基行 S61
    ;(candidates().onSelect as Function)([rows(root)[0]], rows(root)[0])
    await flush()
    ;(candidates().onSelect as Function)([rows(root)[0], rows(root)[2]], rows(root)[2])
    await flush()
    expect(serials.value.map(row => row.scopeDetailId ?? row.scopeId)).toEqual([7, 61])
    expect(serials.value[0].quantity).toBe(1)
    const pagination = findByTestId(root, 'device-pagination')!.props!
    ;(pagination['onUpdate:limit'] as Function)(2)
    await (pagination.onPagination as Function)()
    await flush()
    // pageSize=2 后本页候选行为 D7/D8；取消全选只移除本页，S61 翻页保留
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
    const { root, serials, projectId, app } = setup(10)
    await flush()
    ;(findByTestId(root, 'device-candidates')!.props!.onSelect as Function)([rows(root)[0]], rows(root)[0])
    await flush()
    api.getDeliveryScopePage.mockRejectedValueOnce(new Error('offline'))
    // 换货项目变更是唯一重载入口；重载失败保留已选草稿，候选清空
    projectId.value = 11
    await flush()
    expect(serials.value[0].scopeDetailId).toBe(7)
    expect(rows(root)).toEqual([])
    app.unmount()
  })
})
