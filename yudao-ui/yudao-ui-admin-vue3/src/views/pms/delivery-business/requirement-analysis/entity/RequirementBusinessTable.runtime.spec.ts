import { describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import RequirementBusinessTable from './RequirementBusinessTable.vue'
import template from '../template-20261013.json'
const columns = template.formRulesJson.find(rule => rule.type === 'RequirementBusinessTable')!.props!.columns!
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

describe('requirement running business table', () => {
  it('edits by row, preserves unrelated fields, and supports adding and deleting rows', async () => {
    const rows = ref<any[]>([{ deviceName: '原设备', serialNumber: 'SN1', businessSubnet: '10.0.0.0/24', remark: '保留' }])
    let state: any
    const wrapper = defineComponent({ setup: () => () => h(RequirementBusinessTable, { modelValue: rows.value, columns,
      'onUpdate:modelValue': value => { rows.value = value }, disabled: false,
      ref: value => { if (value) state = (value as any).$.setupState } }) })
    const mounted = mount(wrapper, {}, { ElTable: passthrough, ElTableColumn: tableColumn, ElInput: passthrough })
    try {
      state.updateCell({ index: 0, field: 'deviceName', value: '新设备' }); await nextTick()
      expect(rows.value[0]).toEqual({ deviceName: '新设备', serialNumber: 'SN1', businessSubnet: '10.0.0.0/24', remark: '保留' })
      state.addRow(); await nextTick(); expect(rows.value).toHaveLength(2)
      expect(Object.keys(rows.value[1])).toHaveLength(8)
      state.removeRow(0); await nextTick(); expect(rows.value).toHaveLength(1)
    } finally { mounted.app.unmount() }
  })
  it('blocks editing and row operations for frozen versions', () => {
    const rows = [{ deviceName: '冻结设备' }]
    const mounted = mount(RequirementBusinessTable, { modelValue: rows, columns, disabled: true }, { ElTable: passthrough, ElTableColumn: tableColumn })
    try {
      const state = (mounted.vm as any).$.setupState
      state.updateCell({ index: 0, field: 'deviceName', value: '篡改' }); state.addRow(); state.removeRow(0)
      expect(rows).toEqual([{ deviceName: '冻结设备' }])
    } finally { mounted.app.unmount() }
  })
})
