import { defineComponent, h, inject, provide, type Component, type InjectionKey } from 'vue'
import { mount as mountBase, passthrough } from '../platform/dynamic-form/components/runtimeTestHarness'
export * from '../platform/dynamic-form/components/runtimeTestHarness'

const rowKey: InjectionKey<{ row: Record<string, any>; $index: number }> = Symbol('cutover-table-row')
const Row = defineComponent({ props: ['row', 'index'], setup(props, { slots }) {
  provide(rowKey, { get row() { return props.row }, get $index() { return props.index } })
  return () => h('tr', slots.default?.())
} })
export const tableControls = {
  ElTable: defineComponent({ props: ['data'], setup(props, { slots }) {
    return () => h('table', (props.data || []).map((row: Record<string, any>, index: number) => h(Row, { row, index, key: index }, slots)))
  } }),
  ElTableColumn: defineComponent({ props: ['prop'], setup(props, { slots }) {
    const row = inject(rowKey)
    return () => h('td', row ? slots.default?.(row) || (props.prop ? String(row.row[props.prop] ?? '') : '') : [])
  } })
}
export const mount = (component: Component, props: Record<string, unknown> = {}, components: Record<string, Component> = {}) =>
  mountBase(component, props, {
    ElInput: passthrough, ElInputNumber: passthrough, ElSelect: passthrough, ElOption: passthrough,
    ElRadio: passthrough, ElRadioGroup: passthrough, ElRadioButton: passthrough,
    ElCheckbox: passthrough, ElDatePicker: passthrough, ElSkeleton: passthrough,
    ...components, ...tableControls
  })
