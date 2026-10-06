import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import Preview from './TrainingPrintPreview.vue'
import * as files from '@/api/pms/platform/file'
import * as delivery from '@/api/pms/platform/delivery'
import { getTraining } from '@/api/pms/engineering/training'
import { mount, textOf, passthrough, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const spies=vi.hoisted(() => ({ download:vi.fn(),outputPdf:vi.fn(),error:vi.fn(),warning:vi.fn(),canWrite:true }))
vi.mock('@/hooks/web/useMessage',()=>({ useMessage:()=>({error:spies.error,warning:spies.warning}) }))
vi.mock('@/api/pms/platform/file',()=>({ initializeUpload:vi.fn(),completeUpload:vi.fn() }))
vi.mock('@/api/pms/platform/delivery',()=>({ getDeliveryTypes:vi.fn(),registerMaterial:vi.fn() }))
vi.mock('@/api/pms/engineering/training',()=>({getTraining:vi.fn()}))
vi.mock('@/utils/permission',()=>({checkPermi:(permissions:string[])=>spies.canWrite && permissions.some(permission=>['pms:file:upload','pms:delivery:operate'].includes(permission))}))
vi.mock('@/utils/dict',()=>({ DICT_TYPE:{PMS_TRAINING_TYPE:'types'},getStrDictOptions:()=>[] }))
vi.mock('@/views/pms/platform/dynamic-form/components/registerDynamicFormComponents',()=>({registerDynamicFormComponents:()=>{}}))
vi.mock('@/api/pms/platform/dynamic-form',()=>({}))
vi.mock('@/views/pms/platform/dynamic-form/components/dynamicFormCodec',()=>({ decodeDynamicForm:()=>({rule:[],option:{form:{}}}) }))
vi.mock('html2pdf.js',()=>({default:()=>{ const worker:any={set:()=>worker,from:()=>worker,outputPdf:spies.outputPdf};return worker }}))
const find=(root:TestNode,predicate:(node:TestNode)=>boolean):TestNode|undefined=>predicate(root)?root:root.children.map(child=>find(child,predicate)).find(Boolean)
const record={id:7,projectId:9,version:1,status:2,code:'TRAIN-7',name:'Training'}
const flush=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
const open=async()=>{
 const view=mount(Preview,{}, { Dialog:defineComponent({setup:(_, {slots})=>()=>h('section',[slots.default?.(),slots.footer?.()])}),ElSelect:passthrough,ElOption:passthrough,FormCreate:passthrough })
 await (view.vm as any).open(record,'Project 9');await flush()
 const paper=find(view.root,node=>node.type==='div'&&String(node.props?.class).includes('training-print-paper'))!
 ;(paper as any).querySelectorAll=()=>[]
 return view
}
const exportPdf=async(view:ReturnType<typeof mount>,register=true)=>{
 await (find(view.root,node=>node.type==='button'&&textOf(node).includes(register ? '自动登记交付件' : '下载预览 PDF'))!.props!.onClick as Function)();await flush()
}
beforeEach(()=>{
 vi.clearAllMocks()
 spies.canWrite=true
 vi.stubGlobal('document',{ fonts:{ready:Promise.resolve()},createElement:()=>({click:spies.download}) })
 vi.mocked(getTraining).mockResolvedValue(record)
 spies.outputPdf.mockResolvedValue(new Blob(['%PDF-1.4 test'],{type:'application/pdf'}))
 vi.mocked(files.initializeUpload).mockResolvedValue({artifactId:10,sessionId:11} as any)
 vi.mocked(files.completeUpload).mockResolvedValue({referenceId:12} as any)
 vi.mocked(delivery.registerMaterial).mockResolvedValue({id:13} as any)
 vi.mocked(delivery.getDeliveryTypes).mockResolvedValue([{id:1,typeCode:'TRAINING_RECORD',name:'Training',category:'工程交付',allowedMediaJson:'["pdf"]',maxSizeBytes:52428800,enabled:true}])
})
afterEach(()=>vi.unstubAllGlobals())
it('persists the generated PDF through common delivery before allowing download',async()=>{
 const view=await open();await exportPdf(view)
 expect(delivery.registerMaterial).toHaveBeenCalledWith(expect.objectContaining({ownerModule:'IMP',entityType:'training',entityId:7,typeCode:'TRAINING_RECORD',fileReferenceId:12,sourceKind:'GENERATED'}))
 const uploaded=vi.mocked(files.completeUpload).mock.calls[0][2] as File
 expect(uploaded.type).toBe('application/pdf');expect(uploaded.name).toBe('TRAIN-7.pdf')
 expect(vi.mocked(delivery.registerMaterial).mock.invocationCallOrder[0]).toBeLessThan(spies.download.mock.invocationCallOrder[0])
 expect(spies.error).not.toHaveBeenCalled();view.app.unmount()
})
it('does not download on failed registration and reuses the same PDF and upload on retry',async()=>{
 vi.mocked(delivery.registerMaterial).mockRejectedValueOnce(new Error('registration failed'))
 const view=await open();await exportPdf(view)
 expect(spies.download).not.toHaveBeenCalled();expect(spies.error).toHaveBeenCalled()
 await exportPdf(view)
 expect(spies.outputPdf).toHaveBeenCalledTimes(1);expect(files.completeUpload).toHaveBeenCalledTimes(1)
 expect(delivery.registerMaterial).toHaveBeenCalledTimes(2);expect(spies.download).toHaveBeenCalledTimes(1)
 view.app.unmount()
})
it('rejects an outdated preview before uploading or downloading',async()=>{
 vi.mocked(getTraining).mockResolvedValue({...record,version:2})
 const view=await open();await exportPdf(view)
 expect(files.initializeUpload).not.toHaveBeenCalled();expect(spies.download).not.toHaveBeenCalled()
 expect(spies.error).toHaveBeenCalledWith('培训记录已变化，请关闭预览后重新打开');view.app.unmount()
})

it('read-only preview cannot generate an unregistered formal PDF',async()=>{
 spies.canWrite=false
 const view=await open()
 expect(find(view.root,node=>node.type==='button'&&textOf(node).includes('PDF'))).toBeUndefined()
 expect(spies.outputPdf).not.toHaveBeenCalled();expect(spies.download).not.toHaveBeenCalled()
 expect(files.initializeUpload).not.toHaveBeenCalled();expect(delivery.registerMaterial).not.toHaveBeenCalled()
 view.app.unmount()
})
it('a rejected write never falls back to unregistered PDF generation',async()=>{
 vi.mocked(files.initializeUpload).mockRejectedValueOnce(new Error('closed-project'))
 const view=await open();await exportPdf(view)
 expect(spies.download).not.toHaveBeenCalled();expect(spies.error).toHaveBeenCalledWith('closed-project')
 expect(delivery.registerMaterial).not.toHaveBeenCalled()
 expect(find(view.root,node=>node.type==='button'&&textOf(node).includes('只读下载'))).toBeUndefined()
 view.app.unmount()
})
