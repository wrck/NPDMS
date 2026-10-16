import { defineComponent, h } from 'vue'
import { expect, it } from 'vitest'
import Form from './BusinessEntityForm.vue'
import { mount, passthrough } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import type { TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const input = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], setup: (props, { emit }) => () => h('input', { value: props.modelValue, onChange: (value: unknown) => emit('update:modelValue', value) }) })
const testForm = defineComponent({ setup: (_, { expose, slots }) => { expose({ validate: async () => true }); return () => h('form', slots.default?.()) } })
const inputs = (node: TestNode): TestNode[] => node.type === 'input' ? [node] : node.children.flatMap(inputs)
it('displays persisted timestamp dates without changing them on save and preserves an explicit clear', async () => {
  const fields = [{ code: 'debugTime', name: '调试时间', type: 'DATETIME', readable: true, writable: true }]
  const mounted = mount(Form, { writableFields: fields, initialValues: { debugTime: new Date(2026, 9, 12, 6, 6).getTime() } },
    { ElForm: testForm, ElFormItem: passthrough, ElDatePicker: input })
  try {
    expect(inputs(mounted.root)[0].props?.value).toBe('2026-10-12 06:06:00')
    expect(await (mounted.vm as any).buildInput()).toEqual({})
    ;(inputs(mounted.root)[0].props!.onChange as Function)('2026-10-12 07:06:00')
    expect(await (mounted.vm as any).buildInput()).toEqual({ debugTime: '2026-10-12 07:06:00' })
    ;(inputs(mounted.root)[0].props!.onChange as Function)('')
    expect(await (mounted.vm as any).buildInput()).toEqual({ debugTime: null })
  } finally { mounted.app.unmount() }
})
it('omits unchanged required and unreadable fields and sends explicit optional clears', async () => {
  const fields = ['title','memo','secret'].map(code => ({ code, name: code, type: 'TEXT', required: code === 'title', readable: code !== 'secret', writable: true }))
  const mounted = mount(Form, { writableFields: fields, initialValues: { title: 'keep', memo: 'clear' } }, { ElForm: testForm, ElFormItem: passthrough, ElInput: input })
  expect(await (mounted.vm as any).buildInput()).toEqual({})
  ;(inputs(mounted.root)[1].props!.onChange as Function)('')
  expect(await (mounted.vm as any).buildInput()).toEqual({ memo: null })
  mounted.app.unmount()
})
it('presentation restores grid labels and hides host fields without clearing their stored values', async () => {
  const fields=['name','location','conclusion'].map(code=>({code,name:code,type:'TEXT',required:false,readable:true,writable:true}))
  const mounted=mount(Form,{writableFields:fields,initialValues:{name:'host project',location:'site',conclusion:'kept'},appearance:{columns:2,labelPosition:'top',fields:{name:{hidden:true},location:{label:'工勘地点',span:2,order:0},conclusion:{order:1}}}},{ElForm:testForm,ElFormItem:passthrough,ElInput:input})
  try {
    expect(inputs(mounted.root).map(node=>node.props?.value)).toEqual(['site','kept'])
    expect(await (mounted.vm as any).buildInput()).toEqual({})
    ;(inputs(mounted.root)[0].props!.onChange as Function)('new site')
    expect(await (mounted.vm as any).buildInput()).toEqual({location:'new site'})
  } finally {mounted.app.unmount()}
})
