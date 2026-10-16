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
const checkbox=defineComponent({props:['modelValue','disabled'],emits:['update:modelValue'],setup:(props,{emit,slots})=>()=>h('check',{value:props.modelValue,disabled:props.disabled,onChange:(value:boolean)=>emit('update:modelValue',value)},slots.default?.())})
it('keeps base and copied feature choices independent and preserves them after CAS rejection',async()=>{
 const api={base:'/test',fieldDefaults:vi.fn(async()=>({fields:[]})),fieldConfiguration:vi.fn(async()=>({version:7,fields:[],profiles:[]})),saveFieldConfiguration:vi.fn().mockRejectedValueOnce(new Error('version conflict')).mockResolvedValue({version:8})}
 const view=mount(Configuration,{api},{ElDialog:dialog,ElTable:table,ElTableColumn:column,ElInput:input,ElInputNumber:input,ElSelect:input,ElCheckbox:checkbox})
 const click=async(label:string)=>{const node=all(view.root).find(item=>item.type==='button'&&textOf(item)===label);expect(node,`Missing button: ${label}`).toBeDefined();await(node!.props!.onClick as Function)();await flush()}
 const feature=(label:string)=>{const node=all(view.root).find(item=>item.type==='check'&&textOf(item)===label);expect(node,`Missing feature: ${label}`).toBeDefined();return node!}
 const change=async(node:TestNode,value:unknown)=>{(node.props!.onChange as Function)(value);await flush()}
 try{
  await click('字段配置')
  expect(feature('启用通用附件').props?.value).toBe(false);expect(feature('启用独立业务审批').props?.value).toBe(false)
  await change(feature('启用通用附件'),true);await click('复制为新配置')
  expect(feature('启用通用附件').props?.value).toBe(true);expect(feature('启用独立业务审批').props?.value).toBe(false)
  await change(feature('启用通用附件'),false);await change(feature('启用独立业务审批'),true)
  await click('保存配置')
  expect(api.saveFieldConfiguration).toHaveBeenCalledWith(expect.objectContaining({version:7,features:{attachments:true,approvals:false},profiles:[expect.objectContaining({features:{attachments:false,approvals:true}})]}))
  expect(textOf(view.root)).toContain('version conflict');expect(feature('启用独立业务审批').props?.value).toBe(true)
  const selected=all(view.root).find(node=>node.type==='input'&&node.props?.['aria-label']==='命名配置')!
  await change(selected,'');expect(feature('启用通用附件').props?.value).toBe(true);expect(feature('启用独立业务审批').props?.value).toBe(false)
  await click('保存配置');expect(api.saveFieldConfiguration.mock.calls[1][0]).toMatchObject({version:7,features:{attachments:true,approvals:false}})
 }finally{view.app.unmount()}
})
it('saves the inherited configuration with CAS and keeps rejected edits for correction',async()=>{
 const api={base:'/test',fieldDefaults:vi.fn(async()=>({fields:[{code:'title',name:'Title',readable:true,writable:true,displayOrder:0,listVisible:true,searchable:true,sortable:true}]})),fieldConfiguration:vi.fn(async()=>({version:3,fields:[{code:'title',label:null,displayOrder:null,listVisible:null,searchable:null,sortable:null}]})),saveFieldConfiguration:vi.fn().mockRejectedValueOnce(new Error('version conflict')).mockResolvedValue({version:4,fields:[]})}
 const changed=vi.fn(),mounted=mount(Configuration,{api,onChanged:changed},{ElDialog:dialog,ElTable:table,ElTableColumn:column,ElInput:input,ElInputNumber:input,ElCheckbox:passthrough})
 const click=async(label:string)=>{const target=all(mounted.root).find(node=>node.type==='button'&&textOf(node)===label)!;await (target.props!.onClick as Function)();await flush()}
 try{
  await click('字段配置');const title=all(mounted.root).find(node=>node.type==='input'&&node.props?.value==='Title')!
  expect(title,'nullable configuration attributes inherit model defaults').toBeDefined()
  ;(title.props!.onChange as Function)('Configured title');await nextTick()
  confirm.mockRejectedValueOnce(new Error('cancel'));expect(await (mounted.vm as any).requestLeave()).toBe(false)
  confirm.mockResolvedValueOnce(undefined);expect(await (mounted.vm as any).requestLeave()).toBe(true)
  await click('保存配置')
  expect(api.saveFieldConfiguration).toHaveBeenLastCalledWith({version:3,profiles:[],features:{attachments:false,approvals:false},fields:[{code:'title',label:'Configured title',displayOrder:0,listVisible:true,searchable:true,sortable:true,formVisible:true,tabVisible:true}]})
  expect(textOf(mounted.root)).toContain('version conflict');expect(changed).not.toHaveBeenCalled()
  expect(all(mounted.root).some(node=>node.props?.value==='Configured title')).toBe(true)
  await click('保存配置');expect(changed).toHaveBeenCalledTimes(1);expect(all(mounted.root).some(node=>node.type==='dialog')).toBe(false)
 }finally{mounted.app.unmount()}
})
it('copies a named profile without changing its source and saves form and detail presentation with its default selection',async()=>{
 const api={base:'/test',fieldDefaults:vi.fn(async()=>({fields:[{code:'title',name:'Title',readable:true,writable:true,listVisible:true},{code:'lines',name:'Lines',readable:true,writable:true,children:[{code:'description'}]}]})),
  fieldConfiguration:vi.fn(async()=>({version:7,fields:[],profiles:[{key:'full',name:'完整配置',formRevisionId:101,fields:[]}],defaultProfile:'full'})),
  formOptions:vi.fn(async()=>[{name:'简洁表单',layout:{binding:{formRevisionId:202},revisionNo:1}}]),saveFieldConfiguration:vi.fn().mockResolvedValue({version:8})}
 const mounted=mount(Configuration,{api,projectId:20},{ElDialog:dialog,ElTable:table,ElTableColumn:column,ElInput:input,ElInputNumber:input,ElSelect:input,ElCheckbox:checkbox})
 const click=async(label:string)=>{const target=all(mounted.root).find(node=>node.type==='button'&&textOf(node)===label)!;await (target.props!.onClick as Function)();await flush()}
 const change=async(node:TestNode,value:unknown)=>{(node.props!.onChange as Function)(value);await flush()}
 try{
  await click('字段配置');expect(api.formOptions).toHaveBeenCalledWith(20)
  await click('复制为新配置')
  expect(all(mounted.root).find(node=>node.type==='input'&&node.props?.['aria-label']==='表单模板')?.props?.value).toBe(101)
  await change(all(mounted.root).find(node=>node.type==='input'&&node.props?.['aria-label']==='配置名称')!,'简洁配置')
  await change(all(mounted.root).find(node=>node.type==='input'&&node.props?.['aria-label']==='表单模板')!,202)
  const columns=all(mounted.root).filter(node=>node.type==='column')
  await change(all(columns.find(node=>node.props?.label==='表单')!).find(node=>node.type==='check')!,false)
  await change(all(columns.find(node=>node.props?.label==='明细页签')!).filter(node=>node.type==='check')[1],false)
  await change(all(mounted.root).find(node=>node.type==='check'&&textOf(node)==='默认配置')!,true)
  await click('保存配置')
  const saved=api.saveFieldConfiguration.mock.calls[0][0]
  expect(saved.version).toBe(7);expect(saved.profiles).toHaveLength(2)
  expect(saved.profiles[0]).toMatchObject({key:'full',name:'完整配置',formRevisionId:101})
  expect(saved.profiles[0].fields.find(field=>field.code==='title').formVisible).toBe(true)
  const compact=saved.profiles[1];expect(compact).toMatchObject({name:'简洁配置',formRevisionId:202});expect(saved.defaultProfile).toBe(compact.key)
  expect(compact.fields.find(field=>field.code==='title').formVisible).toBe(false)
  expect(compact.fields.find(field=>field.code==='lines').tabVisible).toBe(false)
  expect(saved.fields.find(field=>field.code==='title').formVisible).toBe(true)
 }finally{mounted.app.unmount()}
})
