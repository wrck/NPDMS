import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Uploader from './DeliveryUploader.vue'
import * as files from '@/api/pms/platform/file'
import * as delivery from '@/api/pms/platform/delivery'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
vi.mock('@/api/pms/platform/file', () => ({ initializeUpload: vi.fn(), completeUpload: vi.fn() }))
vi.mock('@/api/pms/platform/delivery', () => ({ registerMaterial: vi.fn() }))
const find = (root: TestNode, type: string): TestNode | undefined => root.type === type ? root : root.children.map(child => find(child, type)).find(Boolean)
const flush = async () => { for (let i=0;i<10;i++) { await Promise.resolve(); await nextTick() } }
const props = { ownerModule:'IMP', entityType:'training', entityId:7,
 type:{ id:1,typeCode:'TRAINING_RECORD',name:'培训记录',category:'BUSINESS_DOCUMENT',allowedMediaJson:'["application/pdf"]',maxSizeBytes:1024,enabled:true } }
const select = async (view: ReturnType<typeof mount>, size = 2) => {
 await (find(view.root,'input')!.props!.onChange as Function)({ target:{ files:[{ name:'record.pdf',size,type:'application/pdf' }] } })
}
const submit = async (view: ReturnType<typeof mount>) => {
 await (find(view.root,'button')!.props!.onClick as Function)(); await flush()
}
beforeEach(() => {
 vi.clearAllMocks()
 vi.mocked(files.initializeUpload).mockResolvedValue({ artifactId:10,sessionId:11 } as any)
 vi.mocked(files.completeUpload).mockResolvedValue({ referenceId:12 } as any)
 vi.mocked(delivery.registerMaterial).mockResolvedValue({ id:13 } as any)
})
it('registers the uploaded file under its business type before emitting completion', async () => {
 const completed=vi.fn(), view=mount(Uploader,{ ...props,onCompleted:completed })
 await select(view); await submit(view)
 expect(files.initializeUpload).toHaveBeenCalledWith(expect.objectContaining({ ownerContext:'PLT',objectType:'DELIVERY_MATERIAL',objectId:'IMP:training:7',purposeCode:'TRAINING_RECORD' }),expect.any(String))
 expect(delivery.registerMaterial).toHaveBeenCalledWith(expect.objectContaining({ ownerModule:'IMP',entityType:'training',entityId:7,typeCode:'TRAINING_RECORD',fileReferenceId:12 }))
 expect(completed).toHaveBeenCalledWith({ id:13 }); view.app.unmount()
})
it('retries registration without reuploading after a registration failure', async () => {
 const completed=vi.fn(), view=mount(Uploader,{ ...props,onCompleted:completed })
 vi.mocked(delivery.registerMaterial).mockRejectedValueOnce(new Error('registration failed'))
 await select(view); await submit(view)
 expect(completed).not.toHaveBeenCalled(); expect(textOf(view.root)).toContain('文件已保存')
 await submit(view)
 expect(files.initializeUpload).toHaveBeenCalledTimes(1)
 expect(files.completeUpload).toHaveBeenCalledTimes(1)
 expect(delivery.registerMaterial).toHaveBeenCalledTimes(2)
 expect(completed).toHaveBeenCalledTimes(1); view.app.unmount()
})
it('rejects a file exceeding its delivery type policy before storage', async () => {
 const view=mount(Uploader,props); await select(view,2048); await submit(view)
 expect(files.initializeUpload).not.toHaveBeenCalled(); expect(delivery.registerMaterial).not.toHaveBeenCalled()
 expect(textOf(view.root)).toContain('类型限制'); view.app.unmount()
})

it('retains an exact business identity above JavaScript safe integer precision', async () => {
 const entityId='9007199254740993123'
 const view=mount(Uploader,{...props,entityId});await select(view);await submit(view)
 expect(files.initializeUpload).toHaveBeenCalledWith(expect.objectContaining({objectId:`IMP:training:${entityId}`}),expect.any(String))
 expect(delivery.registerMaterial).toHaveBeenCalledWith(expect.objectContaining({entityId}))
 view.app.unmount()
})
