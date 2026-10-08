import { beforeEach, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import { createProjectBusinessApi } from './index'
import { listDeliveries } from './deliveryCollection'
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

it('project collection uses shared material API without the old model-workbench permission gate',async()=>{
 await listDeliveries({projectId:99,deliverableType:'REPORT'})
 expect(request.get).toHaveBeenCalledWith({url:'/api/v1/pms/business-deliverables',params:{projectId:99,deliverableType:'REPORT'},silentError:true})
})

it('custom business APIs share the client but retain explicit endpoint and protected concurrency fields',async()=>{
 const api=createProjectBusinessApi('/api/v1/pms/site-survey-business')
 await api.action('confirm','9007199254740993',2,'key',{version:99,idempotencyKey:'forged'})
 expect(request.post).toHaveBeenCalledWith({url:'/api/v1/pms/site-survey-business/9007199254740993/confirm',data:{version:2,idempotencyKey:'key'},silentError:true})
 expect(()=>api.action('../delete',11,2,'key')).toThrow()
})

it('forms and atomic extension saves use the same inherited business route',async()=>{
 const api=createProjectBusinessApi('/api/v1/pms/site-survey-business')
 await api.form('9007199254740993')
 const values={name:'survey',$extensions:{definitionRevisionId:'123',expectedVersion:1,values:{extra_flag:true}}}
 await api.action('save-form','9007199254740993',2,'form-key',{values})
 expect(request.get).toHaveBeenCalledWith({url:'/api/v1/pms/site-survey-business/9007199254740993/form',silentError:true})
 expect(request.post).toHaveBeenCalledWith({url:'/api/v1/pms/site-survey-business/9007199254740993/save-form',data:{values,version:2,idempotencyKey:'form-key'},silentError:true})
})

it('version history, form and comparison are inherited routes with lossless revision IDs',async()=>{
 const api=createProjectBusinessApi('/api/v1/pms/version-notes')
 await api.revisions('9007199254740993');await api.revisionValues('9007199254740993','9007199254740994');await api.revisionForm('9007199254740993','9007199254740994')
 await api.compareRevisions('9007199254740993','9007199254740994','9007199254740995')
 expect(request.get).toHaveBeenCalledWith({url:'/api/v1/pms/version-notes/9007199254740993/revisions/9007199254740994',silentError:true})
 expect(request.get).toHaveBeenCalledWith({url:'/api/v1/pms/version-notes/9007199254740993/revisions/compare',params:{left:'9007199254740994',right:'9007199254740995'},silentError:true})
 expect(()=>api.revisionValues(1,'../other')).toThrow()
})

it('rejects unsafe numeric delivery identities before issuing an edit or file request', () => {
  const api = createProjectBusinessApi('/api/v1/pms/notes')
  expect(() => api.editDelivery(1, { id: Number.MAX_SAFE_INTEGER + 1, version: 0 } as any, 'title')).toThrow('业务实体 ID 不合法')
})
