import {defineComponent,h,nextTick} from 'vue'
import {expect,it,vi} from 'vitest'
import Deliveries from './ProjectBusinessDeliveries.vue'
import {mount,passthrough,tableColumn,textOf,type TestNode} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/hooks/web/useMessage',()=>({useMessage:()=>({confirm:vi.fn()})}))
const toggle=defineComponent({props:['modelValue'],emits:['update:modelValue'],setup:(props,{emit})=>()=>h('toggle',{value:props.modelValue,onChange:(value:boolean)=>emit('update:modelValue',value)})})
const all=(node:TestNode):TestNode[]=>[node,...node.children.flatMap(all)]
const flush=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
it('queries upload history for the existing business key and makes history read-only',async()=>{
 const scope={projectId:99,businessType:'NOTE',businessEntityKey:'11'}
 const api={base:'/test',deliveryContext:vi.fn(async()=>scope),deliveries:vi.fn(async()=>({list:[],total:0})),deliveryHistory:vi.fn(async()=>({list:[{id:'1',status:'WITHDRAWN',title:'Old'}],total:1})),completion:vi.fn(async()=>({completed:false})),upload:vi.fn().mockRejectedValue(new Error('unknown upload outcome'))}
 const mounted=mount(Deliveries,{api,entityId:'11'},{ElSwitch:toggle,ElInput:passthrough,ElTable:passthrough,ElTableColumn:tableColumn})
 try{
  await flush();const upload=all(mounted.root).find(node=>node.type==='input'&&node.props?.type==='file')!
  expect(upload).toBeDefined();(upload.props!.onChange as Function)({target:{files:[new File(['sample'],'sample.txt')]}});await flush()
  const originalKey=api.upload.mock.calls[0][2]
  ;(all(mounted.root).find(node=>node.type==='toggle')!.props!.onChange as Function)(true);await flush()
  expect(api.deliveryHistory).toHaveBeenLastCalledWith('11','ATTACHMENT',1)
  expect(api.completion).toHaveBeenLastCalledWith({...scope,deliverableType:'ATTACHMENT'})
  expect(all(mounted.root).some(node=>node.type==='input'&&node.props?.type==='file')).toBe(false)
  expect(all(mounted.root).find(node=>Array.isArray(node.props?.data))?.props?.data).toEqual([{id:'1',status:'WITHDRAWN',title:'Old'}])
  expect(api.upload).toHaveBeenCalledTimes(1)
  ;(all(mounted.root).find(node=>node.type==='toggle')!.props!.onChange as Function)(false);await flush()
  const retry=all(mounted.root).find(node=>node.type==='button'&&textOf(node)==='重试原上传')!
  await (retry.props!.onClick as Function)();await flush();expect(api.upload.mock.calls[1][2]).toBe(originalKey)
 }finally{mounted.app.unmount()}
})
