import { defineComponent, h, inject, nextTick, provide, ref, type InjectionKey } from 'vue'
import { describe, expect, it } from 'vitest'
import type { CutoverPlanSupportArrangement } from '@/api/pms/cutover'
import { findByTestId, mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Support from './CutoverSupportArrangements.vue'

const rowKey: InjectionKey<{ row: CutoverPlanSupportArrangement; $index: number }> = Symbol('row')
const Row = defineComponent({ props: ['row', 'index'], setup(props, { slots }) {
  provide(rowKey, { get row() { return props.row }, get $index() { return props.index } })
  return () => h('tr', slots.default?.())
} })
const controls = {
  ElTable: defineComponent({ props: ['data'], setup(props, { slots }) {
    return () => h('table', props.data.map((row: CutoverPlanSupportArrangement, index: number) =>
      h(Row, { row, index, key: index }, slots)))
  } }),
  ElTableColumn: defineComponent({ setup(_, { slots }) {
    const row = inject(rowKey)!
    return () => h('td', slots.default?.(row))
  } }),
  ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough, ElDatePicker: passthrough
}
const contacts = [
  { contactId: '601', contactVersion: 1, projectId: '9', sourceContactId: '31', name: '样例甲', mobile: '00000000001', phone: null, department: null, title: null },
  { contactId: '602', contactVersion: 2, projectId: '9', sourceContactId: '32', name: '样例乙', mobile: '00000000002', phone: null, department: null, title: null }
]

describe('保障人员明细', () => {
  it('客户可显式手填，选择已有记录后锁定电话，重新手填清除引用', async () => {
    const rows = ref<CutoverPlanSupportArrangement[]>([{ arrangementId: null, roleCode: 'CUSTOMER',
      personName: '', dutyDescription: '验收', phone: '', arrivalTime: 1, customerContactReference: null }])
    const page = mount(defineComponent({ setup: () => () => h(Support, {
      modelValue: rows.value, contacts, 'onUpdate:modelValue': value => { rows.value = value }
    }) }), {}, controls)
    const change = async (id: string, value: string) => {
      await (findByTestId(page.root, id)!.props!['onUpdate:modelValue'] as (value: string) => void)(value)
      await nextTick()
    }
    expect(findByTestId(page.root, 'support-customer-0')?.props?.['allow-create']).toBeDefined()
    await change('support-customer-0', '验收手填客户')
    await change('support-phone-0', '00000000003')
    expect(rows.value[0]).toMatchObject({ personName: '验收手填客户', phone: '00000000003', customerContactReference: null })
    await change('support-customer-0', '601')
    expect(findByTestId(page.root, 'support-phone-0')?.props?.disabled).toBe(true)
    expect(rows.value[0].customerContactReference?.contactId).toBe('601')
    await change('support-customer-0', '另一位手填客户')
    expect(rows.value[0]).toMatchObject({ personName: '另一位手填客户', phone: '', customerContactReference: null })
    expect(findByTestId(page.root, 'support-phone-0')?.props?.disabled).toBe(false)
    page.app.unmount()
  })

  it('新增同角色两人、逐行选择客户，删除其中一行不覆盖另一行', async () => {
    const rows = ref<CutoverPlanSupportArrangement[]>([])
    const page = mount(defineComponent({ setup: () => () => h(Support, {
      modelValue: rows.value, contacts, 'onUpdate:modelValue': value => { rows.value = value }
    }) }), {}, controls)
    const click = async (id: string) => {
      const node = findByTestId(page.root, id)
      expect(node, id).toBeDefined()
      await (node!.props!.onClick as () => void)()
      await nextTick()
    }
    const select = async (id: string, value: string) => {
      const node = findByTestId(page.root, id)
      expect(node, id).toBeDefined()
      await (node!.props!['onUpdate:modelValue'] as (value: string) => void)(value)
      await nextTick()
    }
    await click('add-support')
    await click('add-support')
    await select('support-role-0', 'CUSTOMER')
    await select('support-role-1', 'CUSTOMER')
    await select('support-customer-0', '601')
    await select('support-customer-1', '602')
    expect(rows.value.map(row => row.personName)).toEqual(['样例甲', '样例乙'])
    expect(rows.value.map(row => row.customerContactReference?.contactId)).toEqual(['601', '602'])
    await click('remove-support-0')
    expect(rows.value).toHaveLength(1)
    expect(rows.value[0]).toMatchObject({ personName: '样例乙', phone: '00000000002', customerContactReference: { contactId: '602', contactVersion: 2 } })
    await select('support-role-0', 'DP_RND')
    expect(rows.value[0]).toMatchObject({ roleCode: 'DP_RND', personName: '', phone: '', customerContactReference: null })
    page.app.unmount()
  })

  it('批准后的联系信息编辑不允许增删人员或修改角色职责', () => {
    const page = mount(Support, { modelValue: [{ arrangementId: '1', roleCode: 'CUSTOMER', personName: '样例甲',
      dutyDescription: '保障', phone: '00000000001', arrivalTime: 1 }], contacts, readonly: true, patchApproved: true }, controls)
    expect(findByTestId(page.root, 'add-support')).toBeUndefined()
    expect(findByTestId(page.root, 'remove-support-0')).toBeUndefined()
    expect(findByTestId(page.root, 'support-role-0')?.props?.disabled).toBe(true)
    page.app.unmount()
  })
})
