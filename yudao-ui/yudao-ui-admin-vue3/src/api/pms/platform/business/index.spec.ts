import { beforeEach, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { createProjectBusinessApi } from './index'
vi.mock('@/config/axios',()=>({default:{get:vi.fn(),post:vi.fn(),put:vi.fn(),delete:vi.fn()}}))
vi.mock('../file',()=>({createAccessTicket:vi.fn()}))
beforeEach(()=>vi.resetAllMocks())
it('two businesses reuse the client with their own inherited routes',async()=>{
 const first=createProjectBusinessApi('/api/v1/pms/direct-notes'),second=createProjectBusinessApi('/api/v1/pms/direct-reports')
 await first.model();await first.create({projectId:99,title:'note'},'a');await second.create({projectId:99,title:'report'},'b')
 await first.update('9007199254740993',{title:'changed'},2,'c');await second.remove(3,1,'d')
 expect(request.get).toHaveBeenCalledWith({url:'/api/v1/pms/direct-notes/model',silentError:true})
 expect(request.post).toHaveBeenNthCalledWith(1,{url:'/api/v1/pms/direct-notes',data:{values:{projectId:99,title:'note'},idempotencyKey:'a'},silentError:true})
 expect(request.put).toHaveBeenCalledWith({url:'/api/v1/pms/direct-notes/9007199254740993',data:{values:{title:'changed'},version:2,idempotencyKey:'c'},silentError:true})
 expect(request.delete).toHaveBeenCalledWith({url:'/api/v1/pms/direct-reports/3',params:{version:1},headers:{'Idempotency-Key':'d'},silentError:true})
 expect(JSON.stringify([vi.mocked(request.get).mock.calls,vi.mocked(request.post).mock.calls])).not.toContain('business-models')
})
it('rejects external routes, path injection and rounded numeric identities',()=>{
 expect(()=>createProjectBusinessApi('https://other.invalid/api')).toThrow();expect(()=>createProjectBusinessApi('/api/v1/pms/../admin')).toThrow()
 const api=createProjectBusinessApi('/api/v1/pms/direct-notes');expect(()=>api.get('../other')).toThrow();expect(()=>api.get(9007199254740993)).toThrow()
})
it('uploads all four ownership fields to the business endpoint',async()=>{
 const api=createProjectBusinessApi('/api/v1/pms/direct-notes'),scope={projectId:99,businessType:'IT_DIRECT_NOTE',businessEntityKey:'11',deliverableType:'REPORT'}
 await api.upload(scope,new File(['a'],'note.txt'),'upload-key');const sent=vi.mocked(request.post).mock.calls[0][0]
 expect(sent.url).toBe('/api/v1/pms/direct-notes/11/deliverables');for(const [key,value] of Object.entries(scope))expect(sent.data.get(key)).toBe(String(value))
 expect(sent.headers['Idempotency-Key']).toBe('upload-key')
})
