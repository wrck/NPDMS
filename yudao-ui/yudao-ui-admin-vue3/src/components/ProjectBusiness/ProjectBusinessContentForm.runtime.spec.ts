import {defineComponent,h,ref,nextTick} from 'vue'
import {expect,it,vi} from 'vitest'
import ContentForm from './ProjectBusinessContentForm.vue'
import type {FieldVO} from '@/api/pms/platform/businessmodel'
import {mount,passthrough,type TestNode} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('../BusinessEntity/DeclaredBusinessFormLayout.vue',()=>({default:defineComponent({setup(_, {expose}){expose({buildInput:async()=>({})});return()=>h('div','frozen layout')}})}))
const fields:FieldVO[]=['equipmentId','contactName','contactPhone'].map(code=>({code,name:code,type:code==='equipmentId'?'NUMBER':'TEXT',required:false,readable:true,writable:true}))
const testForm=defineComponent({setup(_, {expose,slots}){expose({validate:async()=>true});return()=>h('form',slots.default?.())}})
const input=defineComponent({props:['modelValue'],emits:['update:modelValue'],setup(props,{emit}){return()=>h('input',{value:props.modelValue,onChange:(value:unknown)=>emit('update:modelValue',value)})}})
const nodes=(node:TestNode,type:string):TestNode[]=>node.type===type?[node]:node.children.flatMap(child=>nodes(child,type))
const wrapper=(presentation?:unknown)=>defineComponent({setup(_, {expose}){
  const body=ref<InstanceType<typeof ContentForm>>()
  expose({buildInput:()=>body.value!.buildInput()})
  return()=>h(ContentForm,{ref:body,writableFields:fields,initialValues:{equipmentId:'9223372036854775806',contactName:'客户甲',contactPhone:'123'},presentation:presentation as any},{
    'field-equipmentId':({value,update,disabled}:any)=>h('device-picker',{value,disabled,onPick:update}),
    'field-contactName':({value,update,form}:any)=>h('contact-picker',{value,onPick:(name:string)=>{update(name);form.contactPhone='456'}})
  })
}})
it('native field slots retain long IDs, share contact autofill, and use the ordinary field patch',async()=>{
  const mounted=mount(wrapper(),{}, {ElForm:testForm,ElFormItem:passthrough,ElInput:input})
  try{
    expect(nodes(mounted.root,'device-picker')[0].props?.value).toBe('9223372036854775806')
    expect(await (mounted.vm as any).buildInput()).toEqual({})
    ;(nodes(mounted.root,'contact-picker')[0].props!.onPick as Function)('客户乙')
    await nextTick()
    expect(nodes(mounted.root,'input')[0].props?.value).toBe('456')
    expect(await (mounted.vm as any).buildInput()).toEqual({contactName:'客户乙',contactPhone:'456'})
    ;(nodes(mounted.root,'device-picker')[0].props!.onPick as Function)(null)
    expect(await (mounted.vm as any).buildInput()).toEqual({equipmentId:null,contactName:'客户乙',contactPhone:'456'})
  }finally{mounted.app.unmount()}
})
it('a frozen binding owns its field so a native field slot cannot replace it',async()=>{
  const mounted=mount(wrapper({layout:{binding:{fieldBindings:{device:'equipmentId'}}},extensions:{fields:{},version:0},definitions:[]}),{}, {ElForm:testForm,ElFormItem:passthrough,ElInput:input})
  try{expect(nodes(mounted.root,'device-picker')).toHaveLength(0);expect(await (mounted.vm as any).buildInput()).toEqual({})}
  finally{mounted.app.unmount()}
})
