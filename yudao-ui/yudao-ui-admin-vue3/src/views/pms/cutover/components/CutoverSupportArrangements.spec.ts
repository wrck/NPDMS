import { defineComponent, h, inject, nextTick, provide, ref, type PropType, type InjectionKey } from 'vue'
import { describe, expect, it } from 'vitest'
import type { CutoverPlanSupportArrangement } from '@/api/pms/cutover'
import { findByTestId, mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Support from './CutoverSupportArrangements.vue'

const rowKey: InjectionKey<{ row: CutoverPlanSupportArrangement; $index: number }> = Symbol('row')
const Row = defineComponent({ props: { row: { type: Object as PropType<CutoverPlanSupportArrangement>, required: true }, index: { type: Number, required: true } }, setup(props, { slots }) {
  provide(rowKey, { get row() { return props.row }, get $index() { return props.index } })
  return () => h('tr', slots.default?.())
} })
const controls = {
  ElTable: defineComponent({ props: { data: { type: Array as PropType<CutoverPlanSupportArrangement[]>, required: true } }, setup(props, { slots }) {
    return () => h('table', props.data.map((row: CutoverPlanSupportArrangement, index: number) =>
      h(Row, { row, index, key: index }, slots)))
  } }),
  ElTableColumn: defineComponent({ setup(_, { slots }) {
    const row = inject(rowKey)!
    return () => h('td', slots.default?.(row))
  } }),
  ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough, ElDatePicker: passthrough
}
const personChoices = { CUSTOMER: [
  { reference: { sourceCode: 'PROJECT_MEMBER' as const, id: '601', version: 1 }, name: '样例甲', phone: '00000000001' },
  { reference: { sourceCode: 'PROJECT_MEMBER' as const, id: '602', version: 2 }, name: '样例乙', phone: '00000000002' }
] }

describe('保障人员明细', () => {
  it('候选异步加载后同一人员自动更新引用版本，保留本轮填写内容', async () => {
    const rows = ref<CutoverPlanSupportArrangement[]>([
      { arrangementId: '1', roleCode: 'CUSTOMER', personName: '样例甲', dutyDescription: '客户保障', phone: '自填电话', arrivalTime: 123,
        personReference: { sourceCode: 'PROJECT_MEMBER', id: '601', version: 0 } },
      { arrangementId: '2', roleCode: 'DP_SECOND_LINE', personName: '技术支持', dutyDescription: '二线保障', phone: '保留电话', arrivalTime: 456,
        personReference: { sourceCode: 'SYSTEM_USER', id: '2108559111685582849', version: '1792460873037' } }
    ])
    const choices = ref({})
    const page = mount(defineComponent({ setup: () => () => h(Support, {
      modelValue: rows.value, personChoices: choices.value, 'onUpdate:modelValue': value => { rows.value = value }
    }) }), {}, controls)
    await nextTick()
    expect(rows.value[0].personReference?.version).toBe(0)
    choices.value = { ...personChoices, DP_SECOND_LINE: [{ reference: { sourceCode: 'SYSTEM_USER', id: '2108559111685582849', version: '1792490161000000' }, name: '技术支持', phone: null }] }
    await nextTick()
    await nextTick()
    expect(rows.value[0]).toMatchObject({ personReference: { id: '601', version: 1 }, personName: '样例甲', phone: '自填电话', dutyDescription: '客户保障', arrivalTime: 123 })
    expect(rows.value[1]).toMatchObject({ personReference: { id: '2108559111685582849', version: '1792490161000000' }, phone: '保留电话', dutyDescription: '二线保障', arrivalTime: 456 })
    page.app.unmount()
  })

  it('不替换失效人员，不按同名自动绑定手填人员，也不接受其他角色候选', async () => {
    const rows = ref<CutoverPlanSupportArrangement[]>([
      { arrangementId: '1', roleCode: 'CUSTOMER', personName: '已删除客户', dutyDescription: '保障', phone: '123', arrivalTime: 1,
        personReference: { sourceCode: 'PROJECT_MEMBER', id: '999', version: 0 } },
      { arrangementId: '2', roleCode: 'CUSTOMER', personName: '样例甲', dutyDescription: '保障', phone: '123', arrivalTime: 1 },
      { arrangementId: '3', roleCode: 'DP_RND', personName: '样例甲', dutyDescription: '保障', phone: '123', arrivalTime: 1,
        personReference: { sourceCode: 'SYSTEM_USER', id: '601', version: 0 } }
    ])
    const before = structuredClone(rows.value.map(row => ({ ...row, personReference: row.personReference && { ...row.personReference } })))
    const page = mount(defineComponent({ setup: () => () => h(Support, {
      modelValue: rows.value, personChoices, 'onUpdate:modelValue': value => { rows.value = value }
    }) }), {}, controls)
    await nextTick()
    expect(rows.value).toEqual(before)
    page.app.unmount()
  })

  it.each([{ readonly: true }, { readonly: true, patchApproved: true }])('历史和已批准联系信息不自动改写引用 %j', async mode => {
    const rows = [{ roleCode: 'CUSTOMER' as const, personName: '样例甲', dutyDescription: '保障', phone: '123', arrivalTime: 1,
      personReference: { sourceCode: 'PROJECT_MEMBER' as const, id: '601', version: 0 } }]
    const changes: unknown[] = []
    const page = mount(Support, { modelValue: rows, personChoices, ...mode, 'onUpdate:modelValue': value => changes.push(value) }, controls)
    await nextTick()
    expect(changes).toEqual([])
    expect(rows[0].personReference.version).toBe(0)
    page.app.unmount()
  })

  it('工程师只能选择对应系统角色候选，换角色清除原选择，保留大ID字符串', async () => {
    const rows = ref<CutoverPlanSupportArrangement[]>([{ arrangementId: null, roleCode: 'DP_FIRST_LINE',
      personName: '', dutyDescription: '验收', phone: '', arrivalTime: 1 }])
    const choices = { DP_FIRST_LINE: [{ reference: { sourceCode: 'SYSTEM_USER' as const, id: '2108559111685582849', version: '1792460873037' }, name: '一线样例', phone: '123' }] }
    const page = mount(defineComponent({ setup: () => () => h(Support, {
      modelValue: rows.value, personChoices: choices, 'onUpdate:modelValue': value => { rows.value = value }
    }) }), {}, controls)
    const select = async (id: string, value: string) => {
      await (findByTestId(page.root, id)!.props!['onUpdate:modelValue'] as (value: string) => void)(value)
      await nextTick()
    }
    expect(findByTestId(page.root, 'support-system-0')!.props!['allow-create']).toBe(false)
    await select('support-system-0', '手填工程师')
    expect(rows.value[0].personName).toBe('')
    await select('support-system-0', 'SYSTEM_USER:2108559111685582849:1792460873037')
    expect(rows.value[0]).toMatchObject({ personName: '一线样例', personReference: { id: '2108559111685582849', sourceCode: 'SYSTEM_USER', version: '1792460873037' } })
    await select('support-role-0', 'DP_RND')
    expect(rows.value[0]).toMatchObject({ personName: '', phone: '', personReference: null })
    page.app.unmount()
  })
  it('客户可手填或选择项目成员并带入电话，重新手填清除引用', async () => {
    const rows = ref<CutoverPlanSupportArrangement[]>([{ arrangementId: null, roleCode: 'CUSTOMER',
      personName: '', dutyDescription: '验收', phone: '', arrivalTime: 1, customerContactReference: null }])
    const page = mount(defineComponent({ setup: () => () => h(Support, {
      modelValue: rows.value, personChoices, 'onUpdate:modelValue': value => { rows.value = value }
    }) }), {}, controls)
    const change = async (id: string, value: string) => {
      await (findByTestId(page.root, id)!.props!['onUpdate:modelValue'] as (value: string) => void)(value)
      await nextTick()
    }
    expect(findByTestId(page.root, 'support-customer-0')?.props?.['allow-create']).toBeDefined()
    await change('support-customer-0', '验收手填客户')
    await change('support-phone-0', '00000000003')
    expect(rows.value[0]).toMatchObject({ personName: '验收手填客户', phone: '00000000003', customerContactReference: null })
    await change('support-customer-0', 'PROJECT_MEMBER:601:1')
    expect(findByTestId(page.root, 'support-phone-0')?.props?.disabled).toBe(false)
    expect(rows.value[0].personReference?.id).toBe('601')
    await change('support-customer-0', '另一位手填客户')
    expect(rows.value[0]).toMatchObject({ personName: '另一位手填客户', phone: '', customerContactReference: null })
    expect(findByTestId(page.root, 'support-phone-0')?.props?.disabled).toBe(false)
    page.app.unmount()
  })

  it('新增同角色两人、逐行选择客户，删除其中一行不覆盖另一行', async () => {
    const rows = ref<CutoverPlanSupportArrangement[]>([])
    const page = mount(defineComponent({ setup: () => () => h(Support, {
      modelValue: rows.value, personChoices, 'onUpdate:modelValue': value => { rows.value = value }
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
    await select('support-customer-0', 'PROJECT_MEMBER:601:1')
    await select('support-customer-1', 'PROJECT_MEMBER:602:2')
    expect(rows.value.map(row => row.personName)).toEqual(['样例甲', '样例乙'])
    expect(rows.value.map(row => row.personReference?.id)).toEqual(['601', '602'])
    await click('remove-support-0')
    expect(rows.value).toHaveLength(1)
    expect(rows.value[0]).toMatchObject({ personName: '样例乙', phone: '00000000002', personReference: { sourceCode: 'PROJECT_MEMBER', id: '602', version: 2 } })
    await select('support-role-0', 'DP_RND')
    expect(rows.value[0]).toMatchObject({ roleCode: 'DP_RND', personName: '', phone: '', customerContactReference: null })
    page.app.unmount()
  })

  it('批准后的联系信息编辑不允许增删人员或修改角色职责', () => {
    const page = mount(Support, { modelValue: [{ arrangementId: '1', roleCode: 'CUSTOMER', personName: '样例甲',
      dutyDescription: '保障', phone: '00000000001', arrivalTime: 1 }], personChoices, readonly: true, patchApproved: true }, controls)
    expect(findByTestId(page.root, 'add-support')).toBeUndefined()
    expect(findByTestId(page.root, 'remove-support-0')).toBeUndefined()
    expect(findByTestId(page.root, 'support-role-0')?.props?.disabled).toBe(true)
    page.app.unmount()
  })
})
