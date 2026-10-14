import { describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import SolutionEquipmentTable from './SolutionEquipmentTable.vue'
import { equipmentRow, readSolutionEquipment } from './solutionEquipment'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

const product = { productCode: 'P1', productName: '防火墙', productModel: 'FW', projectQty: 20, orderQty: 12, deviceCount: 3 }
describe('solution equipment list', () => {
  it('uses order quantities and keeps unavailable quantities empty', () => {
    expect(equipmentRow(product)).toMatchObject({ productCode: 'P1', deviceName: '防火墙', quantity: '12' })
    expect(equipmentRow({ ...product, orderQty: undefined }).quantity).toBe('')
  })
  it('keeps deployment and version rows independent without mutating historical source data', () => {
    const topology = { deploy: [{ sn: 'S1', deviceName: '设备1', location: '机柜1', remark: '安装备注' }],
      software: [{ sn: 'S1', version: 'v1', remark: '版本备注' }, { sn: 'S2', model: '设备2', version: 'v2' }] }
    const before = JSON.stringify(topology)
    expect(readSolutionEquipment({ topology, section: 'deploy' })).toMatchObject([{ deviceName: '设备1', location: '机柜1', version: '', remark: '安装备注' }])
    expect(readSolutionEquipment({ topology, section: 'software' })).toMatchObject([{ version: 'v1', remark: '版本备注' }, { deviceName: '设备2', version: 'v2' }])
    expect(JSON.stringify(topology)).toBe(before)
  })
  it('adds rows, selects products and preserves manually entered location and version', async () => {
    const rows = ref<any[]>([])
    let state: any
    const wrapper = defineComponent({ setup: () => () => h(SolutionEquipmentTable, { modelValue: rows.value,
      'onUpdate:modelValue': value => { rows.value = value }, products: [product], section: 'deploy', readOnly: false,
      ref: value => { if (value) state = (value as any).$.setupState } }) })
    const mounted = mount(wrapper, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough })
    try {
      expect(state.columns.map((column: any) => column.field)).toEqual(['deviceName', 'quantity', 'location', 'remark'])
      state.addRow(); await nextTick()
      Object.assign(rows.value[0], { productCode: 'P1', location: '机柜1/U2', version: 'v3', remark: '备注' })
      state.selectProduct(rows.value[0])
      expect(rows.value[0]).toEqual({ productCode: 'P1', deviceName: '防火墙', quantity: '12', location: '机柜1/U2', version: 'v3', remark: '备注' })
      state.removeRow(0); await nextTick(); expect(rows.value).toEqual([])
    } finally { mounted.app.unmount() }
  })
  it('keeps approved equipment rows read-only', () => {
    const rows = [equipmentRow(product)]
    const mounted = mount(SolutionEquipmentTable, { modelValue: rows, products: [product], section: 'software', readOnly: true }, { ElTable: passthrough, ElTableColumn: tableColumn })
    try {
      const state = (mounted.vm as any).$.setupState
      expect(state.columns.map((column: any) => column.field)).toEqual(['deviceName', 'quantity', 'version', 'remark'])
      state.addRow(); state.removeRow(0); state.synchronize(); state.selectProduct(rows[0])
      expect(rows).toEqual([equipmentRow(product)])
    } finally { mounted.app.unmount() }
  })
})
