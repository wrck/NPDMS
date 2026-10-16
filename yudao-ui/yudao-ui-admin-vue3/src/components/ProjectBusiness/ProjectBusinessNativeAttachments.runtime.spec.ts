import { defineComponent, h, nextTick, provide, ref } from 'vue'
import { formContextKey, type FormContext } from 'element-plus'
import { beforeEach, expect, it, vi } from 'vitest'
import Attachments from './ProjectBusinessNativeAttachments.vue'
import { mount, textOf, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const mocks = vi.hoisted(() => ({list:vi.fn(),reference:vi.fn(),withdraw:vi.fn()}))
vi.mock('@/api/pms/platform/delivery', () => ({listMaterials:mocks.list,withdrawMaterial:mocks.withdraw}))
vi.mock('@/api/pms/platform/file', () => ({getReference:mocks.reference}))
vi.mock('@/hooks/web/useMessage', () => ({useMessage:()=>({confirm:vi.fn()})}))
vi.mock('@/utils', () => ({generateUUID:()=>`key-${Math.random()}`}))
vi.mock('@/components/PmsFileArtifact', async () => {
  const {defineComponent,h,inject} = await import('vue')
  const {formContextKey} = await import('element-plus')
  return {PmsFileUploader:defineComponent({props:['artifactId','expectedReferenceVersion'],emits:['completed'],setup:(props,{emit})=>()=>h('button',{'data-upload':true,...props,onClick:()=>emit('completed',{})},'上传')}),
    PmsFileReferenceList:defineComponent({setup:()=>{const form=inject(formContextKey,undefined);return()=>h('p',{'data-reference':true,'data-read-disabled':!!form?.disabled},'附件')}})}
})
const policy = {ownerModule:'IMP',entityType:'arrival',objectType:'ARRIVAL',purpose:'ARRIVAL_SIGN_DOCUMENT',referenceKey:'arrival-sign-document',accept:'.txt'}
const all = (node:TestNode):TestNode[] => [node,...node.children.flatMap(all)]
const flush = async () => {for(let i=0;i<15;i++){await Promise.resolve();await nextTick()}}
const click = async (root:TestNode,label:string) => {
  const target=all(root).find(node=>node.type==='button'&&textOf(node)===label)
  expect(target, label).toBeDefined(); await (target!.props!.onClick as Function)(); await flush()
}
beforeEach(() => {vi.clearAllMocks();mocks.list.mockResolvedValue([]);mocks.reference.mockResolvedValue(null)})
it('loads the existing upload slot when readonly history switches back to editing',async()=>{
  const readonly=ref(true)
  mocks.reference.mockResolvedValue({artifactId:'90071992547409930',referenceVersion:4,status:'ACTIVE'})
  const mounted=mount(defineComponent({setup:()=>()=>h(Attachments,{api:{base:'/api/v1/pms/arrival-business'} as any,entityId:'33',policy,readonly:readonly.value})}))
  try{
    await flush();expect(mocks.list).toHaveBeenCalled();expect(mocks.reference).not.toHaveBeenCalled()
    expect(all(mounted.root).some(node=>node.props?.['data-upload'])).toBe(false)
    readonly.value=false;await flush()
    expect(mocks.reference).toHaveBeenCalledTimes(1)
    const upload=all(mounted.root).find(node=>node.props?.['data-upload'])
    expect(upload).toBeDefined();expect(upload!.props?.artifactId).toBe('90071992547409930')
    expect(upload!.props?.expectedReferenceVersion).toBe(4)
  }finally{mounted.app.unmount()}
})
it('collects through inherited save and retries an unknown result with the same key and version',async()=>{
  const api={base:'/api/v1/pms/arrival-business',get:vi.fn().mockResolvedValue({available:true,concurrencyBasis:8}),update:vi.fn().mockRejectedValueOnce(new Error('unknown outcome')).mockResolvedValue({outcome:'SAVED'})}
  mocks.reference.mockResolvedValue({artifactId:'90071992547409930',referenceVersion:4,status:'ACTIVE'})
  const mounted=mount(Attachments,{api,entityId:'33',policy})
  try{
    await flush()
    const upload=all(mounted.root).find(node=>node.props?.['data-upload'])!
    expect(upload.props?.artifactId).toBe('90071992547409930');expect(upload.props?.expectedReferenceVersion).toBe(4)
    await click(mounted.root,'上传')
    expect(api.update.mock.calls[0].slice(0,3)).toEqual(['33',{},8])
    await click(mounted.root,'重试归集已上传附件')
    expect(api.update.mock.calls[1]).toEqual(api.update.mock.calls[0])
    expect(api.get).toHaveBeenCalledTimes(1)
  }finally{mounted.app.unmount()}
})
it('does not save the previous entity after a delayed lookup when the editor switches records',async()=>{
  let resolve:(value:unknown)=>void=()=>undefined
  const id=ref('33'),api={base:'/api/v1/pms/arrival-business',get:vi.fn(()=>new Promise(done=>{resolve=done})),update:vi.fn()}
  const mounted=mount(defineComponent({setup:()=>()=>h(Attachments,{api:api as any,entityId:id.value,policy})}))
  try{
    await flush();await click(mounted.root,'上传');id.value='34';await flush()
    resolve({available:true,concurrencyBasis:8});await flush()
    expect(api.update).not.toHaveBeenCalled()
    expect(mocks.list).toHaveBeenLastCalledWith('IMP','arrival','34','IMP.ARRIVAL_SIGN_DOCUMENT')
  }finally{mounted.app.unmount()}
})
it('keeps native history readable without uploading or withdrawing in readonly mode',async()=>{
  mocks.list.mockResolvedValue([{id:4,status:'ACTIVE',materialKind:'FILE',fileArtifactId:'55',fileBusinessKey:{ownerContext:'IMP',objectType:'ARRIVAL',objectId:'33',purposeCode:'ARRIVAL_SIGN_DOCUMENT',referenceKey:'arrival-sign-document'}}])
  const mounted=mount(Attachments,{api:{base:'/api/v1/pms/arrival-business'},entityId:'33',policy,readonly:true})
  try{await flush();expect(textOf(mounted.root)).toContain('附件');expect(all(mounted.root).some(node=>node.type==='button')).toBe(false)}finally{mounted.app.unmount()}
})
it('keeps attachment reading enabled inside a disabled form while preserving inherited write protection',async()=>{
  mocks.list.mockResolvedValue([{id:4,status:'ACTIVE',materialKind:'FILE',fileArtifactId:'55',fileBusinessKey:{ownerContext:'IMP',objectType:'ARRIVAL',objectId:'33',purposeCode:'ARRIVAL_SIGN_DOCUMENT',referenceKey:'arrival-sign-document'}}])
  const mounted=mount(defineComponent({setup(){
    provide(formContextKey,{disabled:true} as FormContext)
    return()=>h(Attachments,{api:{base:'/api/v1/pms/arrival-business'} as any,entityId:'33',policy})
  }}))
  try{
    await flush()
    expect(all(mounted.root).find(node=>node.props?.['data-reference'])?.props?.['data-read-disabled']).toBe(false)
    expect(all(mounted.root).some(node=>node.props?.['data-upload'])).toBe(false)
    expect(textOf(mounted.root)).not.toContain('撤回材料')
  }finally{mounted.app.unmount()}
})
