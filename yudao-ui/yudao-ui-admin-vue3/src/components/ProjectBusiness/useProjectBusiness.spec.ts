import { afterEach,beforeEach,expect,it,vi } from 'vitest'
import { useProjectBusiness } from './useProjectBusiness'
import type { ProjectBusinessApi } from '@/api/pms/platform/business'
vi.mock('@/utils/auth',()=>({getCurrentUserId:()=>42,getTenantId:()=>7,getVisitTenantId:()=>undefined}))
const storage=new Map<string,string>();let api:ProjectBusinessApi
const row={ref:{tenantId:7,ownerModule:'IT',entityType:'note',entityId:11},fieldValues:{title:'note'},concurrencyBasis:2,available:true}
const saved={outcome:'SAVED',entityRef:row.ref,newConcurrencyBasis:3,operationCode:'save',operationVersion:1}
beforeEach(()=>{
 storage.clear();vi.stubGlobal('sessionStorage',{getItem:(key:string)=>storage.get(key)??null,setItem:(key:string,value:string)=>storage.set(key,value),removeItem:(key:string)=>storage.delete(key)})
 api={base:'/api/v1/pms/direct-notes',model:vi.fn(async()=>({fields:[],operations:[{code:'create',kind:'CREATE',executable:true},{code:'save',kind:'UPDATE',executable:true},{code:'delete',kind:'DELETE',executable:true}]})),page:vi.fn(async()=>({list:[row],total:1})),get:vi.fn(async()=>row),create:vi.fn(async()=>saved),update:vi.fn(async()=>saved),remove:vi.fn(async()=>({...saved,outcome:'DELETED'})),receipt:vi.fn(async()=>null)} as unknown as ProjectBusinessApi
})
afterEach(()=>vi.unstubAllGlobals())
it('retains committed create identity when the follow-up read fails',async()=>{
 const state=useProjectBusiness(()=>api);await state.load();vi.mocked(api.get).mockRejectedValue(new Error('read failed'));await state.execute('create',{projectId:99,title:'private'})
 expect(api.create).toHaveBeenCalledTimes(1);expect(state.current.value?.ref.entityId).toBe(11);expect(state.current.value?.available).toBe(false);expect(state.pending.value).toBeUndefined()
})
it('unknown update retry preserves its key without storing business input',async()=>{
 const state=useProjectBusiness(()=>api);await state.load();await state.open(11);vi.mocked(api.update).mockRejectedValueOnce(new Error('Network Error')).mockResolvedValue(saved as any)
 await state.execute('save',{title:'private text'});const key=vi.mocked(api.update).mock.calls[0][3];expect([...storage.values()].join('')).not.toContain('private text')
 await state.execute('save',{title:'different'});expect(api.update).toHaveBeenCalledTimes(1);await state.execute('save',{title:'private text'});expect(vi.mocked(api.update).mock.calls[1][3]).toBe(key);expect(storage.size).toBe(0)
})
it('delete and recovered delete never reopen a removed row',async()=>{
 const state=useProjectBusiness(()=>api);await state.load();await state.open(11);vi.mocked(api.get).mockClear();await state.execute('delete');expect(state.current.value).toBeUndefined();expect(api.get).not.toHaveBeenCalled()
 await state.open(11);vi.mocked(api.remove).mockRejectedValueOnce(new Error('lost'));await state.execute('delete');vi.mocked(api.get).mockClear();vi.mocked(api.receipt).mockResolvedValue({...saved,outcome:'DELETED'} as any)
 await state.recover();expect(state.current.value).toBeUndefined();expect(api.get).not.toHaveBeenCalled();expect(storage.size).toBe(0)
})
it('stale list responses cannot overwrite the next business page',async()=>{
 const state=useProjectBusiness(()=>api);await state.load();let finish!:(value:any)=>void;vi.mocked(api.page).mockImplementationOnce(()=>new Promise(resolve=>finish=resolve))
 const old=state.loadPage();api={...api,base:'/api/v1/pms/direct-reports',page:vi.fn(async()=>({list:[],total:0}))};await state.load();finish({list:[row],total:1});await old;expect(state.rows.value).toEqual([])
})
it('retains filters across continuation and refresh',async()=>{
 const state=useProjectBusiness(()=>api);await state.load();const filters=[{fieldCode:'title',operator:'LIKE',values:['abc']}] as any
 await state.loadPage(true,filters);filters[0].values[0]='changed';await state.loadPage(false);await state.loadPage(true)
 expect(api.page).toHaveBeenLastCalledWith(1,20,[{fieldCode:'title',operator:'LIKE',values:['abc']}],[])
})
it('a definitive application rejection clears the first intent so corrected input can be submitted',async()=>{
 const state=useProjectBusiness(()=>api);await state.load();await state.open(11);vi.mocked(api.update).mockRejectedValueOnce('error').mockResolvedValue(saved as any)
 await state.execute('save',{title:'invalid'});expect(storage.size).toBe(0)
 await state.execute('save',{title:'corrected'});expect(api.update).toHaveBeenCalledTimes(2)
})

it('custom action shares unknown-result recovery and never calls the CRUD delete endpoint',async()=>{
 const state=useProjectBusiness(()=>api);api.action=vi.fn().mockRejectedValueOnce(new Error('lost')).mockResolvedValue({...saved,operationCode:'confirm'})
 await state.load();state.model.value!.operations.push({code:'confirm',kind:'DOMAIN_COMMAND',executable:true} as any);await state.open(11)
 await state.execute('confirm');const key=vi.mocked(api.action).mock.calls[0][3]
 await state.execute('confirm');expect(vi.mocked(api.action).mock.calls[1][3]).toBe(key);expect(api.remove).not.toHaveBeenCalled();expect(storage.size).toBe(0)
})

it('preserves controlled sorting across pagination and clears it on business change',async()=>{
 const state=useProjectBusiness(()=>api);await state.load();const sorts=[{fieldCode:'title',direction:'ASC'}] as any
 await state.loadPage(true,undefined,sorts);sorts[0].fieldCode='mutated';await state.loadPage(false)
 expect(api.page).toHaveBeenLastCalledWith(2,20,[],[{fieldCode:'title',direction:'ASC'}])
 await state.load();expect(api.page).toHaveBeenLastCalledWith(1,20,[],[])
})

it('embedded views constrain the page and reject a foreign project detail',async()=>{
 const state=useProjectBusiness(()=>api,()=>99);await state.load();expect(api.page).toHaveBeenLastCalledWith(1,20,[],[],99)
 vi.mocked(api.get).mockResolvedValue({...row,fieldValues:{title:'allowed',projectId:99}});expect(await state.open(11)).toBe(true)
 vi.mocked(api.get).mockResolvedValue({...row,fieldValues:{title:'foreign',projectId:100}});expect(await state.open(11)).toBe(false)
 expect(state.current.value?.fieldValues.title).toBe('allowed');expect(state.error.value).toContain('不属于当前项目')
})
