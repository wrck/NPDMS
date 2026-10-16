import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, provide } from 'vue'
import { nativeBusinessKey } from '@/components/ProjectBusiness/native/nativeBusinessContext'
import Arrival from './ProjectArrivalReceiptPanel.vue'
import { mount, passthrough, tableColumn } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const api = vi.hoisted(() => ({ getArrivalPage: vi.fn(), getArrival: vi.fn(), save: vi.fn(), error: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/system/user', () => ({ __v_isRef: false, getSimpleUser: vi.fn() }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ getUser: { id: 8, nickname: '测试用户' } }) }))
vi.mock('@/api/pms/engineering/arrival', () => api)
vi.mock('./saveArrivalReceipt', () => ({ saveArrivalReceipt: api.save, createArrivalUploadAttempt: (file: File) => ({ file }) }))
vi.mock('@/components/PmsFileArtifact', () => ({ PmsFileReferenceList: defineComponent({ setup: () => () => h('file-reference') }) }))
vi.mock('@/utils/dict', () => ({ DICT_TYPE: { PMS_ARRIVAL_STATUS: 'arrival' }, getIntDictOptions: () => [] }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ success: vi.fn(), error: api.error, confirm: vi.fn(), delConfirm: vi.fn() }) }))
const formStub = defineComponent({ setup(_, { slots, attrs, expose }) {
  expose({ validate: async () => true })
  return () => h('form', attrs, slots.default?.())
} })
const apps: { unmount: () => void }[] = []
const render = (readonly = false) => {
  const view = mount(Arrival, { projectId: 1, readonly }, { ElTable: passthrough, ElTableColumn: tableColumn, ElForm: formStub, ElRow: passthrough, ElCol: passthrough, ElUpload: passthrough })
  apps.push(view.app)
  return { ...view, state: (view.vm as any).$.setupState }
}
const file = { name: '签收.txt', size: 5, raw: { name: '签收.txt', size: 5, type: 'text/plain' } }
beforeEach(() => { vi.resetAllMocks(); api.getArrivalPage.mockResolvedValue({ list: [], total: 0 }); api.getArrival.mockImplementation(async id => ({ id, projectId: 1, status: 0, version: 3 })) })
afterEach(() => apps.splice(0).forEach(app => app.unmount()))
it.each(['pending-success','failure'])('mounts native support only after successful receipt details (%s)', async outcome => {
  const mountedSupport=vi.fn()
  const Support=defineComponent({props:['record'],setup(props){mountedSupport(props.record.version);return()=>h('native-support')}})
  const context={Form:Support,api:(_kind:string,original:unknown)=>original,can:()=>true,initialEntityId:()=>undefined,
    registerForm:()=>()=>{},requestLeave:async()=>true}
  let finish!:(value:unknown)=>void,fail!:(reason:unknown)=>void
  api.getArrival.mockImplementationOnce(()=>new Promise((resolve,reject)=>{finish=resolve;fail=reject}))
  let state:any
  const wrapper=defineComponent({setup(){provide(nativeBusinessKey,context as any);return()=>h(Arrival,{ref:(value:any)=>{if(value)state=value.$.setupState},projectId:1})}})
  const view=mount(wrapper,{}, {ElTable:passthrough,ElTableColumn:tableColumn,ElForm:formStub,ElRow:passthrough,ElCol:passthrough,ElUpload:passthrough})
  apps.push(view.app);await nextTick()
  const opening=state.openForm({id:8,projectId:1,status:0})
  await nextTick();expect(mountedSupport).not.toHaveBeenCalled()
  if(outcome==='failure'){fail(new Error('detail unavailable'));await opening;await nextTick();expect(mountedSupport).not.toHaveBeenCalled()}
  else{finish({id:8,projectId:1,status:0,version:13});await opening;await nextTick();expect(mountedSupport).toHaveBeenCalledTimes(1);expect(mountedSupport).toHaveBeenCalledWith(13)}
})
it('keeps signed receipts and their original attachments read-only', async () => {
  const view = render()
  api.getArrival.mockResolvedValue({ id: 8, projectId: 1, status: 1, version: 2, attachmentUrl: 'original-file' })
  await view.state.openForm({ id: 8, projectId: 1, status: 1 }); await nextTick()
  expect(view.state.readOnly).toBe(true)
  await view.state.save()
  expect(api.save).not.toHaveBeenCalled()
  expect(view.state.form.attachmentUrl).toBe('original-file')
})
it('keeps listing and saving inside the enclosing project and queues files without writing a raw URL', async () => {
  const view = render()
  view.state.query.projectId = 99; await view.state.load()
  expect(api.getArrivalPage).toHaveBeenLastCalledWith(expect.objectContaining({ projectId: 1 }))
  await view.state.openForm(); view.state.selectAttachment(file, [file]); await view.state.save()
  expect(api.save).toHaveBeenCalledWith(expect.objectContaining({ projectId: 1, receiverUserId: 8, attachmentUrl: '' }), { file: file.raw })
})
it('loads the actual file reference before editing and retains pending/abnormal versions', async () => {
  const view = render()
  for (const status of [0, 2]) {
    api.getArrival.mockResolvedValue({ id: 9, projectId: 1, status, version: 3, signDocumentArtifactId: 11 })
    await view.state.openForm({ id: 9, projectId: 1, status }); await view.state.save()
    expect(view.state.readOnly).toBe(false)
    expect(api.save).toHaveBeenLastCalledWith(expect.objectContaining({ id: 9, status, version: 3, signDocumentArtifactId: 11 }), undefined)
  }
})
it('blocks saving on detail load failure and in a readonly project workspace', async () => {
  const view = render()
  api.getArrival.mockRejectedValue(new Error('detail unavailable'))
  await view.state.openForm({ id: 9, projectId: 1, status: 0 }); await view.state.save()
  expect(api.save).not.toHaveBeenCalled()
  const readonly = render(true); await readonly.state.openForm(); await readonly.state.save()
  expect(api.save).not.toHaveBeenCalled()
})
it('keeps failed saves open with the selected file available for retry', async () => {
  const view = render()
  await view.state.openForm(); view.state.selectAttachment(file, [file])
  api.save.mockRejectedValueOnce(new Error('upload failed'))
  await view.state.save()
  expect(view.state.formVisible).toBe(true)
  await view.state.save()
  expect(api.save.mock.calls[0][1]).toBe(api.save.mock.calls[1][1])
})
it('rejects unsupported files and clears only the queued selection on removal', async () => {
  const view = render(); await view.state.openForm()
  const invalid = { ...file, name: 'bad.exe' }, list = [invalid]
  view.state.selectAttachment(invalid, list)
  expect(list).toHaveLength(0)
  view.state.selectAttachment(file, [file]); view.state.form.arrivalTime = 2000
  view.state.removeAttachment(); await view.state.save()
  expect(api.save).toHaveBeenCalledWith(expect.objectContaining({ arrivalTime: 2000 }), undefined)
})
it('does not let a stale detail request overwrite a newly opened receipt', async () => {
  const view = render()
  let resolve: (value: unknown) => void = () => {}
  api.getArrival.mockReturnValue(new Promise(done => { resolve = done }))
  const oldRequest = view.state.openForm({ id: 9, projectId: 1, status: 0 })
  await view.state.openForm()
  resolve({ id: 9, projectId: 1, status: 1 }); await oldRequest
  expect(view.state.form.id).toBeUndefined()
  expect(view.state.readOnly).toBe(false)
})
