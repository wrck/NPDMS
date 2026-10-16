import { defineComponent, h, nextTick, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Editor from './ReportDraftEditor.vue'
import { mount, passthrough, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api=vi.hoisted(()=>({updateDraft:vi.fn(),createDraft:vi.fn(),publishVersion:vi.fn(),upload:vi.fn(),success:vi.fn()}))
const pendingFile=vi.hoisted(()=>({value:true}))
vi.mock('@/api/pms/acceptance/acceptance-report',()=>api)
vi.mock('@/utils',()=>({generateUUID:()=>crypto.randomUUID()}))
vi.mock('@/utils/permission',()=>({checkPermi:()=>true}))
vi.mock('@/store/modules/user',()=>({useUserStore:()=>({getUser:{id:1,nickname:'验收人'}})}))
vi.mock('@/hooks/web/useMessage',()=>({useMessage:()=>({success:api.success,warning:vi.fn(),confirm:vi.fn()})}))
vi.mock('@/components/BusinessView/operationHost',()=>({useOperationClient:()=>ref(undefined)}))
vi.mock('@/components/ProjectBusiness/native/nativeBusinessContext',()=>({useNativeBusinessApi:(_kind:string,original:unknown)=>original}))
vi.mock('@/components/PmsFileArtifact',()=>({PmsFileUploader:defineComponent({setup(_, {emit,expose}){
 const pending=ref(pendingFile.value);expose({isBusy:()=>false,hasPendingFile:()=>pending.value,submit:async()=>{await api.upload();pending.value=false;emit('completed')}});return()=>h('input',{type:'file'})
}})}))
const dialog=defineComponent({props:['modelValue'],setup:(props,{slots})=>()=>props.modelValue?h('dialog',[slots.default?.(),slots.footer?.()]):null})
const all=(node:TestNode):TestNode[]=>[node,...node.children.flatMap(all)]
const flush=async()=>{for(let i=0;i<10;i++){await Promise.resolve();await nextTick()}}
beforeEach(()=>{pendingFile.value=true;vi.clearAllMocks();api.upload.mockResolvedValue(undefined);api.updateDraft.mockResolvedValue({reportVersionId:21,reportVersionNo:1,activityVersion:2});api.publishVersion.mockResolvedValue({})})
it('reopens a draft with live attachments and saves without uploading the same file again',async()=>{
 pendingFile.value=false
 const view=mount(Editor,{allowedActions:['QUERY','UPDATE','PUBLISH','FILE_WRITE']},{Dialog:dialog,ElForm:passthrough,ElRow:passthrough,ElCol:passthrough,ElFormItem:passthrough})
 try{
  await(view.vm as any).open({id:11,acceptanceType:'PRELIMINARY',activityStatus:'PENDING',version:1,originKind:'DIRECT'},{id:21,reportVersionNo:1,attachments:[],draftAttachmentCount:1});await flush()
  expect(textOf(view.root)).toContain('本次已上传 1 个')
  const save=all(view.root).find(node=>node.type==='button'&&textOf(node)==='保存')!
  await(save.props!.onClick as Function)();await flush()
  expect(api.upload).not.toHaveBeenCalled();expect(api.updateDraft).toHaveBeenCalledTimes(1);expect(api.publishVersion).toHaveBeenCalledTimes(1)
 }finally{view.app.unmount()}
})
it.each(['success','upload','save','publish'])('defers outer refresh until the combined report flow settles (%s)',async failure=>{
 const readonly=ref(false),changed=vi.fn(()=>{readonly.value=true}),editor=ref<any>()
 if(failure==='upload')api.upload.mockRejectedValueOnce(new Error('upload failed'))
 if(failure==='save')api.updateDraft.mockRejectedValueOnce(new Error('save failed'))
 if(failure==='publish')api.publishVersion.mockRejectedValueOnce(new Error('publish failed'))
 const view=mount(defineComponent({setup:()=>()=>h(Editor,{ref:editor,readonly:readonly.value,allowedActions:['QUERY','UPDATE','PUBLISH','FILE_WRITE'],onChanged:changed})}),{}, {Dialog:dialog,ElForm:passthrough,ElRow:passthrough,ElCol:passthrough,ElFormItem:passthrough})
 try{
  await editor.value.open({id:11,acceptanceType:'PRELIMINARY',activityStatus:'PENDING',version:1,originKind:'DIRECT'}, {id:21,reportVersionNo:1,attachments:[]});await flush()
  const save=all(view.root).find(node=>node.type==='button'&&textOf(node)==='保存')!
  await(save.props!.onClick as Function)().catch(()=>{});await flush()
  expect(api.upload).toHaveBeenCalledTimes(1)
  expect(api.updateDraft).toHaveBeenCalledTimes(failure==='upload'?0:1)
  expect(api.publishVersion).toHaveBeenCalledTimes(['success','publish'].includes(failure)?1:0)
  if(failure==='success'){
   expect(api.upload.mock.invocationCallOrder[0]).toBeLessThan(api.updateDraft.mock.invocationCallOrder[0])
   expect(api.updateDraft.mock.invocationCallOrder[0]).toBeLessThan(api.publishVersion.mock.invocationCallOrder[0])
   expect(api.publishVersion.mock.invocationCallOrder[0]).toBeLessThan(changed.mock.invocationCallOrder[0])
  }
  expect(changed).toHaveBeenCalledTimes(failure==='upload'?0:1)
  expect(api.success).toHaveBeenCalledTimes(failure==='success'?1:0)
  expect(all(view.root).some(node=>node.type==='dialog')).toBe(failure!=='success')
  if(failure==='save'||failure==='publish')expect(textOf(view.root)).toContain('本次已上传 1 个')
 }finally{view.app.unmount()}
})
it('blocks leaving and repeated save while the combined upload is pending',async()=>{
 let finish!:()=>void
 api.upload.mockReturnValueOnce(new Promise<void>(resolve=>{finish=resolve}))
 const view=mount(Editor,{allowedActions:['QUERY','UPDATE','PUBLISH','FILE_WRITE']},{Dialog:dialog,ElForm:passthrough,ElRow:passthrough,ElCol:passthrough,ElFormItem:passthrough})
 const editor=view.vm as any
 try{
  await editor.open({id:11,acceptanceType:'PRELIMINARY',activityStatus:'PENDING',version:1,originKind:'DIRECT'},{id:21,reportVersionNo:1,attachments:[]});await flush()
  const save=all(view.root).find(node=>node.type==='button'&&textOf(node)==='保存')!
  const pending=(save.props!.onClick as Function)();await flush()
  expect(await editor.requestLeave()).toBe(false);expect(editor.discardChanges()).toBe(false)
  await(save.props!.onClick as Function)();expect(api.upload).toHaveBeenCalledTimes(1)
  finish();await pending;await flush();expect(api.publishVersion).toHaveBeenCalledTimes(1)
 }finally{finish?.();view.app.unmount()}
})
