import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import { ed25519 } from '@noble/curves/ed25519'
import { discoverLocalDac, LOCAL_DAC_ORIGINS, confirmLocalDac, localDacRequest, LocalDacError } from './localDac'
const environment = { serverUrl: 'http://npdms.example:8443', webOrigin: 'http://web.example' }
const privateKey = new Uint8Array(32).fill(7)
const otherKey = new Uint8Array(32).fill(9)
const publicHex = (key: Uint8Array = privateKey) => Array.from(ed25519.getPublicKey(key), b => b.toString(16).padStart(2, '0')).join('')
const storage = new Map<string,string>()
let states: Record<number, Record<string,unknown>>
let keys: Record<number, Uint8Array>
let replay: string | undefined
const request = vi.fn(async (address: string, _init?: RequestInit) => {
  const url = new URL(address)
  if (url.pathname !== '/api/v1/desktop/status') return { ok:true, json:async()=>({collectionId:'collection-1'}) }
  const port = Number(url.port)
  if (!states[port]) throw new Error('refused')
  const data = {product:'npdms-dac',protocolVersion:3,status:'READY',...environment,port,
    instanceId:'11111111-1111-4111-8111-111111111111',publicKey:publicHex(keys[port]),...states[port]}
  const message = `npdms-dac-discovery-v1\n${replay ?? url.searchParams.get('nonce')}\n${data.port}\n${data.instanceId}\n3\n${data.status}\n${data.serverUrl}\n${data.webOrigin}`
  const signature = btoa(String.fromCharCode(...ed25519.sign(new TextEncoder().encode(message), keys[port] ?? privateKey)))
  return {ok:true,json:async()=>({...data,signature})}
})
beforeEach(() => {
  states={8280:{}};keys={};replay=undefined;storage.clear();request.mockClear()
  vi.stubGlobal('crypto',webcrypto)
  vi.stubGlobal('window',{localStorage:{getItem:(key:string)=>storage.get(key)??null,setItem:(key:string,value:string)=>storage.set(key,value)}})
  vi.stubGlobal('fetch',request)
})
afterEach(() => {vi.unstubAllGlobals();vi.useRealTimers()})
const pair = async () => {
  const found=await discoverLocalDac(environment)
  if(!('origin' in found)) return found
  const nonce='a'.repeat(64)
  const response=await request(`${found.origin}/api/v1/desktop/status?nonce=${nonce}`,{credentials:'omit',cache:'no-store',redirect:'error'})
  return confirmLocalDac({...environment,nonce,status:await response.json() as import('./localDac').LocalDacStatus})
}
const challengeProof = async (nonce = 'a'.repeat(64)) => {
  const result = await request(`http://127.0.0.1:8280/api/v1/desktop/status?nonce=${nonce}`)
  return { nonce, status: await result.json() as import('./localDac').LocalDacStatus }
}
it('probes only the previously verified port and falls back when it moves', async () => {
  await pair()
  request.mockClear()
  expect((await discoverLocalDac(environment)).state).toBe('ready')
  expect(request.mock.calls.map(([url]) => new URL(url).port)).toEqual(['8280'])
  delete states[8280]
  states[8289] = {}
  request.mockClear()
  expect(await discoverLocalDac(environment)).toMatchObject({ state: 'ready', origin: 'http://127.0.0.1:8289' })
  expect(request.mock.calls.some(([url]) => new URL(url).port === '8289')).toBe(true)
})
it('confirms a native-launch challenge and preserves only the public installation key', async () => {
  expect((await confirmLocalDac({...environment,...await challengeProof()})).state).toBe('ready')
  expect([...storage.values()]).toEqual([publicHex()])
})
it.each(['nonce','signature','environment','port','cancelled','previous-identity'])('rejects %s native confirmation without trusting a new key',async(change)=>{
  const proof=await challengeProof()
  const controller=new AbortController()
  if(change==='nonce') proof.nonce='b'.repeat(64)
  if(change==='signature') proof.status.signature='AA=='
  if(change==='environment') proof.status.webOrigin='http://other'
  if(change==='port') proof.status.port=48182
  if(change==='cancelled') controller.abort()
  if(change==='previous-identity') {await pair();keys[8280]=otherKey;proof.status=(await challengeProof()).status}
  await expect(confirmLocalDac({...environment,...proof,signal:controller.signal})).rejects.toThrow()
  expect([...storage.values()]).toEqual(change==='previous-identity'?[publicHex()]:[])
})
it('does not trust unsigned first discovery and stores only the confirmed public key', async()=>{
  expect((await discoverLocalDac(environment)).state).toBe('connection-required')
  expect(storage.size).toBe(0)
  expect((await pair()).state).toBe('ready')
  expect([...storage.values()]).toEqual([publicHex()])
})
it('finds the last candidate when the first port is unrelated',async()=>{
  states={8280:{product:'other'},8289:{}}
  expect((await pair()).state).toBe('ready')
  expect(await discoverLocalDac(environment)).toMatchObject({state:'ready',origin:'http://127.0.0.1:8289'})
  expect(new Set(request.mock.calls.map(call=>new URL(call[0]).port))).toEqual(new Set(LOCAL_DAC_ORIGINS.map(origin=>new URL(origin).port)))
  for(const [,init] of request.mock.calls) expect(init).toMatchObject({credentials:'omit',cache:'no-store',redirect:'error'})
  for(const [,init] of request.mock.calls) expect(init?.headers).toBeUndefined()
})
it('automatically finds the paired installation after a port change',async()=>{
  await pair();states={8287:{}};request.mockClear()
  await localDacRequest({environment,path:'/saved-connections',ticket:{value:'scoped-ticket',tenantId:1}})
  expect(request).toHaveBeenCalledWith('http://127.0.0.1:8287/api/v1/desktop/saved-connections',expect.objectContaining({headers:expect.objectContaining({'X-NPDMS-DAC-Ticket':'scoped-ticket'})}))
})
it.each(['identity','nonce','port','environment'])('rejects %s confusion without sending a ticket or password',async(change)=>{
  await pair();request.mockClear()
  if(change==='identity') keys[8280]=otherKey
  if(change==='nonce') replay='0'.repeat(64)
  if(change==='port') states[8280]={port:8281}
  if(change==='environment') states[8280]={serverUrl:'http://other.example'}
  await expect(localDacRequest({environment,path:'/tasks/task-1/executions',ticket:{value:'ticket',tenantId:1},method:'POST',body:{password:'never-send'}})).rejects.toBeInstanceOf(LocalDacError)
  expect(request.mock.calls.every(([url])=>new URL(url).pathname==='/api/v1/desktop/status')).toBe(true)
  expect(JSON.stringify(request.mock.calls)).not.toContain('never-send')
})
it('never repeats an uncertain execution POST',async()=>{
  await pair()
  const execute=vi.fn(async(url:string,init?:RequestInit)=>{
    if(url.includes('/executions')) throw new Error('lost acknowledgement')
    return request(url,init)
  })
  vi.stubGlobal('fetch',execute)
  const failure=await localDacRequest({environment,path:'/tasks/task-1/executions',ticket:{value:'ticket',tenantId:1},method:'POST',body:{password:'never-log'}}).catch(e=>e)
  expect(failure).toBeInstanceOf(LocalDacError)
  if (!(failure instanceof LocalDacError)) throw new Error('Expected local DAC error')
  expect(failure.outcomeUnknown).toBe(true)
  expect(JSON.stringify(failure)).not.toContain('never-log')
  expect(execute.mock.calls.filter(([url])=>url.includes('/executions'))).toHaveLength(1)
})
it('reports unreachable candidates without claiming uninstalled',async()=>{
  states={};expect(await discoverLocalDac(environment)).toEqual({state:'unavailable'})
})
it('bounds all unreachable probes',async()=>{
  vi.useFakeTimers()
  vi.stubGlobal('fetch',vi.fn((_url:string,init:RequestInit)=>new Promise((_resolve,reject)=>init.signal?.addEventListener('abort',()=>reject(new Error('aborted'))))))
  const pending=discoverLocalDac(environment)
  await vi.advanceTimersByTimeAsync(8000)
  expect(await pending).toEqual({state:'unavailable'})
})
it('uses the verified cached candidate without starting other probes',async()=>{
  await pair()
  vi.useFakeTimers()
  const signals:AbortSignal[]=[]
  vi.stubGlobal('fetch',vi.fn((url:string,init:RequestInit)=>{
    if(new URL(url).port==='8280') return request(url,init)
    signals.push(init.signal as AbortSignal)
    return new Promise((_resolve,reject)=>init.signal?.addEventListener('abort',()=>reject(new Error('aborted'))))
  }))
  let completed=false
  const pending=discoverLocalDac(environment).then(result=>{completed=true;return result})
  await vi.advanceTimersByTimeAsync(0)
  expect(completed).toBe(true)
  expect(await pending).toMatchObject({state:'ready',origin:'http://127.0.0.1:8280'})
  expect(signals).toHaveLength(0)
})
it('keeps the first verified result when a second signed instance responds later',async()=>{
  await pair()
  states[8281]={instanceId:'22222222-2222-4222-8222-222222222222'}
  vi.useFakeTimers()
  vi.stubGlobal('fetch',vi.fn(async(url:string,init:RequestInit)=>{
    if(new URL(url).port==='8281') await new Promise(resolve=>setTimeout(resolve,1500))
    return request(url,init)
  }))
  let completed=false
  const pending=discoverLocalDac(environment).then(result=>{completed=true;return result})
  await vi.advanceTimersByTimeAsync(100)
  expect(completed).toBe(true)
  expect(await pending).toMatchObject({state:'ready',origin:'http://127.0.0.1:8280'})
  await vi.advanceTimersByTimeAsync(1400)
  expect(await pending).toMatchObject({state:'ready',origin:'http://127.0.0.1:8280'})
})
it('ignores a quick untrusted responder and accepts a later pinned installation',async()=>{
  await pair();states[8280]={};keys[8280]=otherKey;states[8289]={}
  vi.useFakeTimers()
  vi.stubGlobal('fetch',vi.fn(async(url:string,init:RequestInit)=>{
    if(new URL(url).port==='8289') await new Promise(resolve=>setTimeout(resolve,100))
    return request(url,init)
  }))
  const pending=discoverLocalDac(environment)
  await vi.advanceTimersByTimeAsync(100)
  expect(await pending).toMatchObject({state:'ready',origin:'http://127.0.0.1:8289'})
})
it('aborts all pending probes when the caller cancels',async()=>{
  vi.useFakeTimers()
  const signals:AbortSignal[]=[]
  vi.stubGlobal('fetch',vi.fn((_url:string,init:RequestInit)=>new Promise((_resolve,reject)=>{
    signals.push(init.signal as AbortSignal)
    init.signal?.addEventListener('abort',()=>reject(new Error('aborted')))
  })))
  const controller=new AbortController()
  const pending=discoverLocalDac({...environment,signal:controller.signal})
  controller.abort()
  expect(await pending).toEqual({state:'unavailable'})
  expect(signals.every(signal=>signal.aborted)).toBe(true)
})
it.each([[401,'授权已过期'],[403,'授权与当前任务'],[404,'连接不存在'],[409,'连接已变化'],[400,'采集参数无效'],[503,'服务端授权校验']])('retains HTTP %s failure semantics',async(status,reason)=>{
  await pair()
  vi.stubGlobal('fetch',vi.fn(async(url:string,init?:RequestInit)=>url.includes('/status?')?request(url,init):{ok:false,status}))
  const failure=await localDacRequest({environment,path:'/saved-connections',ticket:{value:'ticket',tenantId:1}}).catch(e=>e)
  if (!(failure instanceof LocalDacError)) throw new Error('Expected local DAC error')
  expect(failure.message).toContain(reason)
})
