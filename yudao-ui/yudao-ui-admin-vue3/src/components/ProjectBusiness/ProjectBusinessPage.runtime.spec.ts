import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import Page from './ProjectBusinessPage.vue'
import { mount } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const mocks = vi.hoisted(() => ({ confirm: vi.fn(), build: vi.fn(), get: vi.fn(), page: vi.fn(), writable: true }))
vi.mock('@/components/Dialog/src/Dialog.vue',()=>({default:defineComponent({props:['modelValue','title','width','beforeClose'],setup(props,{slots}){return()=>h('dialog',{open:props.modelValue,title:props.title,width:props.width},[slots.default?.(),slots.footer?.()])}})}))
vi.mock('vue-router', () => ({ onBeforeRouteLeave: vi.fn(), onBeforeRouteUpdate: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => ({ confirm: mocks.confirm, warning: vi.fn() }) }))
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => 42, getTenantId: () => 7, getVisitTenantId: () => undefined }))
vi.mock('@/api/pms/platform/business', () => ({ createProjectBusinessApi: () => ({
  base: '/api/v1/pms/notes', model: async () => ({ fields: [], capabilities: [], operations: [{code:'create',kind:'CREATE',executable:true},{code:'save',kind:'UPDATE',executable:mocks.writable}] }),
  page: mocks.page, get: mocks.get, form: async () => ({ extensions: { fields: {}, version: 0 }, definitions: [] })
}) }))
vi.mock('../BusinessEntity/BusinessEntityList.vue', () => ({ default: { render: () => null } }))
vi.mock('./ProjectBusinessContentForm.vue', () => ({ default: defineComponent({ setup(_, { expose }) { expose({ buildInput: mocks.build }); return () => h('input') } }) }))
vi.mock('./ProjectBusinessHistory.vue', () => ({ default: { render: () => null } }))
vi.mock('./ProjectBusinessDeliveries.vue', () => ({ default: defineComponent({ setup(_, { expose }) { expose({ isBusy: () => false, requestLeave: async () => true }); return () => null } }) }))
vi.mock('./ProjectBusinessFieldConfiguration.vue', () => ({ default: defineComponent({ setup(_, { expose }) { expose({ isBusy: () => false, requestLeave: async () => true }); return () => null } }) }))
const flush = async () => { for(let i=0;i<12;i++) { await Promise.resolve(); await nextTick() } }
beforeEach(() => {
  vi.resetAllMocks(); mocks.writable=true; mocks.build.mockResolvedValue({ title: 'Unsaved' }); mocks.confirm.mockResolvedValue(undefined)
  mocks.page.mockResolvedValue({list:[],total:0}); mocks.get.mockResolvedValue({ref:{entityId:11},fieldValues:{title:'Saved'},concurrencyBasis:0,available:true})
})
it('ordinary edits survive cancelled back, reload and route leave, then allow explicit discard', async () => {
  const mounted = mount(Page,{apiBase:'/api/v1/pms/notes'}); const state=(mounted.vm as any).$.setupState
  try {
    await flush(); await state.edit(11); await flush(); mocks.get.mockClear()
    mocks.confirm.mockRejectedValueOnce(new Error('cancel')); await state.back(); expect(state.editing).toBe(true)
    mocks.confirm.mockRejectedValueOnce(new Error('cancel')); await state.reloadCurrent(); expect(mocks.get).not.toHaveBeenCalled()
    mocks.confirm.mockRejectedValueOnce(new Error('cancel')); expect(await state.requestLeave()).toBe(false)
    await state.back(); expect(state.editing).toBe(false)
  } finally { mounted.app.unmount() }
})
it('invalid unsaved input still asks before leaving and unchanged input does not prompt', async () => {
  const mounted=mount(Page,{apiBase:'/api/v1/pms/notes'}); const state=(mounted.vm as any).$.setupState
  try {
    await flush(); state.create(); await flush(); mocks.build.mockRejectedValueOnce(new Error('invalid')); mocks.confirm.mockRejectedValueOnce(new Error('cancel'))
    expect(await state.requestLeave()).toBe(false); expect(mocks.confirm).toHaveBeenCalledOnce()
    mocks.build.mockResolvedValue({}); mocks.confirm.mockClear(); expect(await state.requestLeave()).toBe(true); expect(mocks.confirm).not.toHaveBeenCalled()
  } finally { mounted.app.unmount() }
})

it('delivery lifecycle is independent of confirmed body editing while preserving write permission', async () => {
  const operationAllowed=(code:string)=>code==='delivery'
  const mounted=mount(Page,{apiBase:'/api/v1/pms/notes',initialEntityId:11,operationAllowed})
  const state=(mounted.vm as any).$.setupState
  try {
    await flush()
    expect(state.updateOperation.executable).toBe(false)
    expect(state.deliveryReadonly).toBe(false)
  } finally { mounted.app.unmount() }
})
it.each([
  {readonly:true},
  {allowedActions:['QUERY']},
  {operationAllowed:()=>false},
  {denyPermission:true},
  {unavailable:true}
])('delivery retains independent scope and read-only guards: %j', async options => {
  const {denyPermission,unavailable,...props}=options
  mocks.writable=!denyPermission
  if(unavailable)mocks.get.mockResolvedValue({ref:{entityId:11},fieldValues:{},available:false})
  const mounted=mount(Page,{apiBase:'/api/v1/pms/notes',initialEntityId:11,...props})
  try { await flush(); expect((mounted.vm as any).$.setupState.deliveryReadonly).toBe(true) }
  finally { mounted.app.unmount() }
})

it('dialog presentation starts at the list and opens only on request, retaining leave protection', async () => {
  const mounted=mount(Page,{apiBase:'/api/v1/pms/notes',dialogEditor:true,title:'工勘',initialEntityId:11})
  const state=(mounted.vm as any).$.setupState
  try {
    await flush();await vi.dynamicImportSettled();await flush()
    expect(state.editing).toBe(false)
    expect(state.scopedRows.map((row:any)=>row.ref.entityId)).toEqual([11])
    await state.edit(11);await vi.dynamicImportSettled();await flush()
    expect(state.editing).toBe(true)
    const done=vi.fn();mocks.confirm.mockRejectedValueOnce(new Error('cancel'))
    await state.closeEditor(done);expect(done).not.toHaveBeenCalled();expect(state.editing).toBe(true)
    mocks.build.mockResolvedValue({});await state.closeEditor(done);expect(done).toHaveBeenCalledOnce()
  } finally {mounted.app.unmount()}
})
it('new-record defaults are awaited before opening the shared editor', async () => {
  let ready!:(value:Record<string,unknown>)=>void
  const prepareCreate=()=>new Promise<Record<string,unknown>>(resolve=>{ready=resolve})
  const mounted=mount(Page,{apiBase:'/api/v1/pms/notes',prepareCreate})
  const state=(mounted.vm as any).$.setupState
  try {
    await flush();const pending=state.create();expect(state.editing).toBe(false)
    ready({name:'project title'});await pending;expect(state.editing).toBe(true);expect(state.scopedInitial.name).toBe('project title')
  } finally {mounted.app.unmount()}
})
