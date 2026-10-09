import {defineComponent,h,nextTick,ref} from 'vue'
import {expect,it,vi} from 'vitest'
import Deliveries from './ProjectBusinessDeliveries.vue'
import {mount,passthrough,tableColumn,textOf,type TestNode} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/hooks/web/useMessage',()=>({useMessage:()=>({confirm:vi.fn()})}))
const drawer=defineComponent({props:['modelValue'],emits:['update:modelValue'],setup:(props,{emit,slots})=>()=>props.modelValue?h('dialog',[h('button',{onClick:()=>emit('update:modelValue',false)},'关闭上传记录'),slots.default?.()]):null})
const all=(node:TestNode):TestNode[]=>[node,...node.children.flatMap(all)]
const flush=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
const click=async(node:TestNode,label:string)=>{const button=all(node).find(item=>item.type==='button'&&textOf(item)===label);expect(button,`Missing button: ${label}`).toBeDefined();await (button!.props!.onClick as Function)();await flush()}
const components={ElDrawer:drawer,ElInput:passthrough,ElTable:passthrough,ElTableColumn:tableColumn}
it('opens a separate readonly upload history without losing the original upload retry',async()=>{
 const scope={projectId:99,businessType:'NOTE',businessEntityKey:'11'}
 const api={base:'/test',deliveryContext:vi.fn(async()=>scope),deliveries:vi.fn(async()=>({list:[],total:0})),deliveryHistory:vi.fn(async()=>({list:[{id:'1',status:'WITHDRAWN',title:'旧附件',fileName:'old.txt',uploadedAt:1791489254786}],total:1})),completion:vi.fn(async()=>({completed:false})),upload:vi.fn().mockRejectedValue(new Error('unknown upload outcome'))}
 const mounted=mount(Deliveries,{api,entityId:'11'},components)
 try{
  await flush();const upload=all(mounted.root).find(node=>node.type==='input'&&node.props?.type==='file')!
  expect(upload).toBeDefined();(upload.props!.onChange as Function)({target:{files:[new File(['sample'],'sample.txt')]}});await flush()
  const originalKey=api.upload.mock.calls[0][2]
  await click(mounted.root,'上传记录')
  expect(api.deliveryHistory).toHaveBeenLastCalledWith('11','ATTACHMENT',1)
  expect(api.completion).toHaveBeenLastCalledWith({...scope,deliverableType:'ATTACHMENT'})
  const history=all(mounted.root).find(node=>node.type==='dialog')!
  expect(textOf(history)).toContain('旧附件')
  expect(textOf(history)).toContain('已移除')
  expect(textOf(history)).not.toContain('1791489254786')
  expect(all(history).some(node=>node.type==='input'&&node.props?.type==='file')).toBe(false)
  expect(all(history).some(node=>node.type==='button'&&/删除|重命名|上传文件/.test(textOf(node)))).toBe(false)
  expect(all(history).some(node=>node.type==='button'&&textOf(node)==='下载')).toBe(false)
  expect(api.upload).toHaveBeenCalledTimes(1)
  await click(mounted.root,'关闭上传记录');await click(mounted.root,'重试上传')
  expect(api.upload.mock.calls[1][2]).toBe(originalKey)
 }finally{mounted.app.unmount()}
})
it('closes history and discards a late response when the business entity changes',async()=>{
 let resolveOld:(value:unknown)=>void=()=>undefined
 const entityId=ref('11'),api={base:'/test',deliveryContext:vi.fn(async(id:string)=>({projectId:99,businessType:'NOTE',businessEntityKey:id})),deliveries:vi.fn(async()=>({list:[],total:0})),completion:vi.fn(async()=>({completed:false})),deliveryHistory:vi.fn().mockImplementationOnce(()=>new Promise(resolve=>{resolveOld=resolve})).mockResolvedValue({list:[{id:'2',status:'ACTIVE',title:'新实体附件',fileName:'new.txt',uploadedAt:1791489254786}],total:1})}
 const mounted=mount(defineComponent({setup:()=>()=>h(Deliveries,{api:api as any,entityId:entityId.value,readonly:true})}),{},components)
 try{
  await flush();await click(mounted.root,'上传记录');entityId.value='12';await flush()
  resolveOld({list:[{id:'1',status:'ACTIVE',title:'旧实体附件'}],total:1});await flush()
  expect(all(mounted.root).some(node=>node.type==='dialog')).toBe(false)
  await click(mounted.root,'上传记录');expect(api.deliveryHistory).toHaveBeenLastCalledWith('12','ATTACHMENT',1)
  expect(textOf(mounted.root)).toContain('新实体附件');expect(textOf(mounted.root)).not.toContain('旧实体附件')
  expect(all(mounted.root).some(node=>node.type==='input'&&node.props?.type==='file')).toBe(false)
 }finally{mounted.app.unmount()}
})
