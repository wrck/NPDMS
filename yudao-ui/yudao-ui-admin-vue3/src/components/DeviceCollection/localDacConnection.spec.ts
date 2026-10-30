import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import SHA256 from 'crypto-js/sha256'
import { prepareLocalDacConnection } from './localDacConnection'
const api=vi.hoisted(()=>({post:vi.fn(),get:vi.fn(),delete:vi.fn(),confirm:vi.fn()}))
vi.mock('@/config/axios',()=>({default:api}))
vi.mock('./localDac',()=>({confirmLocalDac:api.confirm}))
const environment={serverUrl:'http://npdms.example:59191',webOrigin:'http://web.example'}
beforeEach(()=>{
  vi.useFakeTimers();vi.clearAllMocks();vi.stubGlobal('crypto',webcrypto)
  api.post.mockImplementation(async({data})=>({requestId:data.requestId,tenantId:1,expiresInSeconds:90}))
  api.delete.mockResolvedValue(true)
})
afterEach(()=>{vi.unstubAllGlobals();vi.useRealTimers()})
it('sends only the nonce hash to begin and confirms only with owner-polled proof',async()=>{
  const signal=new AbortController().signal
  const connection=await prepareLocalDacConnection(environment,signal)
  const uri=new URL(connection.href),nonce=uri.searchParams.get('challenge')!
  expect(api.post.mock.calls[0][0].data.nonceHash).toBe(SHA256(nonce).toString())
  expect(JSON.stringify(api.post.mock.calls)).not.toContain(nonce)
  expect(api.get).not.toHaveBeenCalled()
  api.get.mockResolvedValue({state:'confirmed',status:{publicKey:'public'}})
  api.confirm.mockResolvedValue({state:'ready'})
  expect(await connection.finish(signal)).toEqual({state:'ready'})
  expect(api.confirm).toHaveBeenCalledWith({...environment,nonce,status:{publicKey:'public'},signal})
})
it('cancellation after a pending poll prevents late confirmation',async()=>{
  let resolve:((value:unknown)=>void)|undefined
  api.get.mockImplementation(()=>new Promise(done=>{resolve=done}))
  const controller=new AbortController()
  const connection=await prepareLocalDacConnection(environment,controller.signal)
  const result=connection.finish(controller.signal).catch(e=>e)
  controller.abort();await connection.cancel()
  resolve?.({state:'confirmed',status:{publicKey:'late'}})
  expect((await result).message).toContain('已取消')
  expect(api.confirm).not.toHaveBeenCalled()
  expect(api.delete).toHaveBeenCalledTimes(1)
})
it('expires without opening an application or accepting a key',async()=>{
  const controller=new AbortController()
  const connection=await prepareLocalDacConnection(environment,controller.signal)
  api.get.mockResolvedValue({state:'pending'})
  const result=connection.finish(controller.signal).catch(e=>e)
  await vi.advanceTimersByTimeAsync(90000)
  expect((await result).message).toContain('超时')
  expect(api.confirm).not.toHaveBeenCalled()
})

it('does not accept confirmation delivered after the deadline even if the poll began earlier',async()=>{
  let resolve:((value:unknown)=>void)|undefined
  api.get.mockImplementation(()=>new Promise(done=>{resolve=done}))
  const signal=new AbortController().signal
  const connection=await prepareLocalDacConnection(environment,signal)
  const result=connection.finish(signal).catch(e=>e)
  vi.setSystemTime(connection.expiresAt+1)
  resolve?.({state:'confirmed',status:{publicKey:'late'}})
  expect((await result).message).toContain('超时')
  expect(api.confirm).not.toHaveBeenCalled()
})
