import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { useBusinessEntity } from './useBusinessEntity'
const identity = vi.hoisted(() => ({ tenant: 7, user: 880001, visit: undefined as number | undefined }))
vi.mock('@/utils/auth', () => ({ getCurrentUserId: () => identity.user, getTenantId: () => identity.tenant, getVisitTenantId: () => identity.visit }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
const storage = new Map<string,string>()
const op = { code: 'save', version: 1, name: 'Save', kind: 'UPDATE', executable: true }
const receipt = { outcome: 'SAVED', operationCode: 'save', operationVersion: 1, entityRef: {tenantId:7,ownerModule:'IT',entityType:'note',entityId:5},newConcurrencyBasis:1 }
beforeEach(() => {
  vi.resetAllMocks();storage.clear();identity.tenant=7;identity.user=880001;identity.visit=undefined
  vi.stubGlobal('sessionStorage', {getItem:(key:string)=>storage.get(key)??null,setItem:(key:string,value:string)=>storage.set(key,value),removeItem:(key:string)=>storage.delete(key)})
  vi.mocked(request.get).mockImplementation(async (query:any)=>query.url.endsWith('/receipt')?null:{ownerModule:'IT',entityType:'note',fields:[],operations:[op],capabilities:[]})
})
afterEach(()=>vi.unstubAllGlobals())
it('retains the idempotency key after an unknown response and refuses a different pending intent', async () => {
  const entity = useBusinessEntity(() => 'IT', () => 'note')
  vi.mocked(request.post).mockRejectedValueOnce(new Error('Network Error')).mockResolvedValue(receipt)
  await expect(entity.execute(op, 5, { title: 'a' }, 0)).rejects.toThrow('Network Error')
  const first = vi.mocked(request.post).mock.calls.at(-1)![0].data.idempotencyKey
  await expect(entity.execute(op, 5, { title: 'b' }, 0)).rejects.toThrow('未确定')
  await entity.execute(op, 5, { title: 'a' }, 0)
  expect(vi.mocked(request.post).mock.calls.at(-1)![0].data.idempotencyKey).toBe(first)
  await entity.execute(op, 5, { title: 'b' }, 1)
  expect(vi.mocked(request.post).mock.calls.at(-1)![0].data.idempotencyKey).not.toBe(first)
})
it('restores the exact key after a fresh page instance while storing no business input or Secret',async()=>{
  const first=useBusinessEntity(()=> 'IT',()=> 'note')
  vi.mocked(request.post).mockRejectedValueOnce(new Error('lost')).mockResolvedValue(receipt)
  const input={title:'private business text',devicePassword:'DO_NOT_PERSIST'}
  await expect(first.execute(op,5,input,0)).rejects.toThrow('lost')
  const key=vi.mocked(request.post).mock.calls.at(-1)![0].data.idempotencyKey
  expect([...storage.values()].join('')).not.toContain('private business text')
  expect([...storage.values()].join('')).not.toContain('DO_NOT_PERSIST')
  const refreshed=useBusinessEntity(()=> 'IT',()=> 'note');await refreshed.loadDetail()
  expect(refreshed.pendingIntent.value?.key).toBe(key)
  await refreshed.execute(op,'5',input,0)
  expect(vi.mocked(request.post).mock.calls.at(-1)![0].data.idempotencyKey).toBe(key)
  expect(storage.size).toBe(0)
})
it('confirms a committed original receipt after refresh without another business POST',async()=>{
  const entity=useBusinessEntity(()=> 'IT',()=> 'note')
  vi.mocked(request.post).mockRejectedValueOnce(new Error('lost'))
  await expect(entity.execute(op,5,{title:'a'},0)).rejects.toThrow('lost')
  const key=vi.mocked(request.post).mock.calls.at(-1)![0].data.idempotencyKey
  const refreshed=useBusinessEntity(()=> 'IT',()=> 'note');await refreshed.loadDetail()
  expect(await refreshed.recover()).toBeNull();expect(storage.size).toBe(1)
  vi.mocked(request.get).mockResolvedValue(receipt)
  expect(await refreshed.recover()).toEqual(receipt)
  expect(request.get).toHaveBeenLastCalledWith({url:'/api/v1/pms/business-models/IT/note/operations/save/receipt',params:{operationVersion:1,idempotencyKey:key},silentError:true})
  expect(request.post).toHaveBeenCalledTimes(1);expect(storage.size).toBe(0)
})
it('isolates intent locators across actor and effective tenant, including visit tenant',async()=>{
  vi.mocked(request.post).mockRejectedValue(new Error('lost'))
  await expect(useBusinessEntity(()=> 'IT',()=> 'note').execute(op,5,{title:'a'},0)).rejects.toThrow('lost')
  const originalKey=vi.mocked(request.post).mock.calls.at(-1)![0].data.idempotencyKey
  identity.user=880002
  await expect(useBusinessEntity(()=> 'IT',()=> 'note').execute(op,5,{title:'b'},0)).rejects.toThrow('lost')
  expect(vi.mocked(request.post).mock.calls.at(-1)![0].data.idempotencyKey).not.toBe(originalKey)
  identity.user=880001;identity.visit=8
  const visitor=useBusinessEntity(()=> 'IT',()=> 'note');await visitor.loadDetail();expect(visitor.pendingIntent.value).toBeUndefined()
  await expect(visitor.execute(op,5,{title:'b'},0)).rejects.toThrow('lost')
  expect(storage.size).toBe(3)
  identity.visit=undefined
  const original=useBusinessEntity(()=> 'IT',()=> 'note');await original.loadDetail();expect(original.pendingIntent.value?.key).toBe(originalKey)
})
it('a denied retry cannot erase an earlier unknown commit, while a first definite rejection can be corrected',async()=>{
  const entity=useBusinessEntity(()=> 'IT',()=> 'note')
  vi.mocked(request.post).mockRejectedValueOnce(new Error('lost')).mockRejectedValueOnce({response:{status:403}})
  await expect(entity.execute(op,5,{title:'a'},0)).rejects.toThrow('lost')
  await expect(entity.execute(op,5,{title:'a'},0)).rejects.toEqual({response:{status:403}})
  expect(storage.size).toBe(1)
  identity.user=880002
  const first=useBusinessEntity(()=> 'IT',()=> 'note')
  vi.mocked(request.post).mockRejectedValueOnce({response:{status:400}})
  await expect(first.execute(op,5,{title:'b'},0)).rejects.toEqual({response:{status:400}})
  expect(storage.size).toBe(1)
})
it('refuses to send a business command when recovery metadata cannot be persisted',async()=>{
  vi.stubGlobal('sessionStorage',{getItem:()=>null,setItem:()=>{throw new Error('storage unavailable')},removeItem:()=>{}})
  await expect(useBusinessEntity(()=> 'IT',()=> 'note').execute(op,5,{title:'a'},0)).rejects.toThrow('storage unavailable')
  expect(request.post).not.toHaveBeenCalled()
})

const deferred = <T=any>() => { let resolve!:(value:T)=>void,reject!:(error:any)=>void;const promise=new Promise<T>((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject} }
it.each(['receipt','rejection'])('a late %s from an old shared create cannot delete the newer unknown create intent',async(settlement)=>{
  const oldFirst=deferred(),oldSecond=deferred(),next=deferred()
  vi.mocked(request.post).mockImplementationOnce(()=>oldFirst.promise).mockImplementationOnce(()=>oldSecond.promise).mockImplementationOnce(()=>next.promise).mockResolvedValue(receipt)
  const one=useBusinessEntity(()=> 'IT',()=> 'note'),two=useBusinessEntity(()=> 'IT',()=> 'note')
  const first=one.execute(op,undefined,{title:'A'});const outcome=first.then(value=>value,error=>error)
  await vi.waitFor(()=>expect(request.post).toHaveBeenCalledTimes(1))
  const second=two.execute(op,undefined,{title:'A'})
  await vi.waitFor(()=>expect(request.post).toHaveBeenCalledTimes(2))
  expect(vi.mocked(request.post).mock.calls[0][0].data.idempotencyKey).toBe(vi.mocked(request.post).mock.calls[1][0].data.idempotencyKey)
  oldSecond.resolve(receipt);await second
  const newer=two.execute(op,undefined,{title:'B'});const unknown=expect(newer).rejects.toThrow('B response lost')
  await vi.waitFor(()=>expect(request.post).toHaveBeenCalledTimes(3))
  const key=vi.mocked(request.post).mock.calls[2][0].data.idempotencyKey
  if(settlement==='receipt')oldFirst.resolve(receipt);else oldFirst.reject({response:{status:400}})
  await outcome
  expect(JSON.parse([...storage.values()][0]).key).toBe(key)
  next.reject(new Error('B response lost'));await unknown
  const refreshed=useBusinessEntity(()=> 'IT',()=> 'note');await refreshed.loadDetail()
  expect(refreshed.pendingIntent.value?.key).toBe(key)
  await refreshed.execute(op,undefined,{title:'B'})
  expect(vi.mocked(request.post).mock.calls[3][0].data.idempotencyKey).toBe(key)
})
it('a late GET recovery cannot erase a newer intent created after another caller recovered the old receipt',async()=>{
  const unknown=useBusinessEntity(()=> 'IT',()=> 'note');vi.mocked(request.post).mockRejectedValueOnce(new Error('A lost'))
  await expect(unknown.execute(op,undefined,{title:'A'})).rejects.toThrow('A lost')
  const first=deferred(),second=deferred(),next=deferred()
  vi.mocked(request.get).mockImplementationOnce(()=>first.promise).mockImplementationOnce(()=>second.promise).mockResolvedValue({fields:[],operations:[op],capabilities:[]})
  const one=useBusinessEntity(()=> 'IT',()=> 'note'),two=useBusinessEntity(()=> 'IT',()=> 'note')
  const a=one.recover(),b=two.recover();first.resolve(receipt);await a
  vi.mocked(request.post).mockImplementationOnce(()=>next.promise).mockResolvedValue(receipt)
  const newer=one.execute(op,undefined,{title:'B'});const lost=expect(newer).rejects.toThrow('B lost')
  await vi.waitFor(()=>expect(request.post).toHaveBeenCalledTimes(2))
  const key=vi.mocked(request.post).mock.calls[1][0].data.idempotencyKey
  second.resolve(receipt);await b
  expect(JSON.parse([...storage.values()][0]).key).toBe(key)
  next.reject(new Error('B lost'));await lost
  const refreshed=useBusinessEntity(()=> 'IT',()=> 'note');await refreshed.loadDetail();expect(refreshed.pendingIntent.value?.key).toBe(key)
  await refreshed.execute(op,undefined,{title:'B'});expect(vi.mocked(request.post).mock.calls[2][0].data.idempotencyKey).toBe(key)
})
