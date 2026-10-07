import {defineComponent,h,provide,inject,nextTick} from 'vue'
import {expect,it,vi} from 'vitest'
import Configuration from './ProjectBusinessFieldConfiguration.vue'
import {mount,passthrough,textOf,type TestNode} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const confirm=vi.hoisted(()=>vi.fn())
vi.mock('@/hooks/web/useMessage',()=>({useMessage:()=>({confirm})}))
const all=(node:TestNode):TestNode[]=>[node,...node.children.flatMap(all)]
const flush=async()=>{for(let i=0;i<10;i++){await Promise.resolve();await nextTick()}}
const table=defineComponent({props:['data'],setup:(props,{slots})=>{provide('rows',()=>props.data);return()=>h('table',slots.default?.())}})
const column=defineComponent({setup:(_,{slots})=>{const rows=inject<()=>any[]>('rows')!;return()=>h('column',rows().map(row=>slots.default?.({row})))}})
const input=defineComponent({props:['modelValue'],emits:['update:modelValue'],setup:(props,{emit})=>()=>h('input',{value:props.modelValue,onChange:(value:unknown)=>emit('update:modelValue',value)})})
const dialog=defineComponent({props:['modelValue'],setup:(props,{slots})=>()=>props.modelValue?h('dialog',[slots.default?.(),slots.footer?.()]):null})
it('saves the inherited configuration with CAS and keeps rejected edits for correction',async()=>{
 const api={base:'/test',fieldDefaults:vi.fn(async()=>({fields:[{code:'title',name:'Title',readable:true,writable:true,displayOrder:0,listVisible:true,searchable:true,sortable:true}]})),fieldConfiguration:vi.fn(async()=>({version:3,fields:[]})),saveFieldConfiguration:vi.fn().mockRejectedValueOnce(new Error('version conflict')).mockResolvedValue({version:4,fields:[]})}
 const changed=vi.fn(),mounted=mount(Configuration,{api,onChanged:changed},{ElDialog:dialog,ElTable:table,ElTableColumn:column,ElInput:input,ElInputNumber:input,ElCheckbox:passthrough})
 const click=async(label:string)=>{const target=all(mounted.root).find(node=>node.type==='button'&&textOf(node)===label)!;await (target.props!.onClick as Function)();await flush()}
 try{
  await click('字段配置');const title=all(mounted.root).find(node=>node.type==='input'&&node.props?.value==='Title')!
  ;(title.props!.onChange as Function)('Configured title');await nextTick()
  confirm.mockRejectedValueOnce(new Error('cancel'));expect(await (mounted.vm as any).requestLeave()).toBe(false)
  confirm.mockResolvedValueOnce(undefined);expect(await (mounted.vm as any).requestLeave()).toBe(true)
  await click('保存配置')
  expect(api.saveFieldConfiguration).toHaveBeenLastCalledWith({version:3,fields:[{code:'title',label:'Configured title',displayOrder:0,listVisible:true,searchable:true,sortable:true}]})
  expect(textOf(mounted.root)).toContain('version conflict');expect(changed).not.toHaveBeenCalled()
  expect(all(mounted.root).some(node=>node.props?.value==='Configured title')).toBe(true)
  await click('保存配置');expect(changed).toHaveBeenCalledTimes(1);expect(all(mounted.root).some(node=>node.type==='dialog')).toBe(false)
 }finally{mounted.app.unmount()}
})
