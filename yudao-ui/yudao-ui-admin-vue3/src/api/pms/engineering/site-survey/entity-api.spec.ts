import { beforeEach, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as api from './entity'
const controlled = vi.hoisted(() => ({ execute: vi.fn(), target: { projectId: 7, nodeKind: 'TASK', nodeId: 4 } }))
vi.mock('@/components/BusinessView/operationClient', async importOriginal => ({ ...(await importOriginal<typeof import('@/components/BusinessView/operationClient')>()), selectionClient: (selection: unknown) => selection ? controlled : undefined }))
vi.mock('@/config/axios', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock('@/api/pms/platform/businessmodel', () => ({ newIdempotencyKey: () => 'compat-key' }))
vi.mock('@/config/axios/service', () => ({ service: { defaults: { transformResponse: [] } } }))
const id = '9007199254740993'
const receipt = (state = '0', deleted = false) => ({ outcome: 'SAVED' as const,
  entityRef: { tenantId: 1, ownerModule: 'SOL', entityType: 'siteSurvey', entityId: id as unknown as number }, newConcurrencyBasis: 5,
  references: [{ kind: 'COMMAND' as const, ownerModule: 'SOL', value: JSON.stringify({ id, projectId: '7', version: 5, state, deleted }) }] })
beforeEach(() => vi.clearAllMocks())
it('keeps native reads and routes independent fixed/extensions save through the public client', async () => {
  vi.mocked(request.post).mockResolvedValue(receipt())
  const survey = { id: id as unknown as number, projectId: 7, code: 'generated', status: 0, name: '工勘', version: 4,
    businessValues: { cabinetReady: false, powerTypes: ['AC'] }, extensionValues: { extra_other: 'value' } }
  await api.getSiteSurvey(survey.id); await api.getSiteSurveyPage({ projectId: 7 }); await api.getDefaultFormSchema(); await api.getFormSchema(50,1)
  await api.saveSiteSurveyReceipt(survey, 'save-key')
  expect(request.post).toHaveBeenCalledWith(expect.objectContaining({url:'/api/v1/pms/business-models/SOL/siteSurvey/operations/save',params:{entityId:id},data:{
    idempotencyKey:'save-key',concurrencyBasis:4,entryKind:'INDEPENDENT',input:{ values:{ projectId:7,name:'工勘',businessValues:survey.businessValues,extensionValues:survey.extensionValues } }
  }}))
  expect(request.put).not.toHaveBeenCalled()
  for(const [command] of vi.mocked(request.get).mock.calls) expect(command.url).toMatch(/^\/api\/v1\/pms\/site-surveys\//)
})
it('creation preserves exact server result id and a retry carries the same key and body', async () => {
  vi.mocked(request.post).mockRejectedValueOnce(new Error('response lost')).mockResolvedValue(receipt())
  const data={projectId:7,name:'工勘'}
  await expect(api.createSiteSurvey(data,'pinned')).rejects.toThrow('response lost')
  expect(await api.createSiteSurvey(data,'pinned')).toBe(id)
  expect(vi.mocked(request.post).mock.calls[0]).toEqual(vi.mocked(request.post).mock.calls[1])
  expect(vi.mocked(request.post).mock.calls[1][0].params).toBeUndefined()
})
it.each(['delete','confirm','reject','archive'] as const)('public %s retains supplied basis/key and real result receipt', async action => {
  vi.mocked(request.post).mockResolvedValue(receipt(action==='delete'?'DELETED':'1',action==='delete'))
  const result=await api.siteSurveyActionReceipt(action,id as unknown as number,4,7,'action-key')
  expect(result.entityRef?.entityId).toBe(id)
  expect(request.post).toHaveBeenCalledWith(expect.objectContaining({url:`/api/v1/pms/business-models/SOL/siteSurvey/operations/${action}`,params:{entityId:id},data:{idempotencyKey:'action-key',concurrencyBasis:4,entryKind:'INDEPENDENT',input:{}}}))
  expect(request.get).not.toHaveBeenCalled()
})
it('refuses wrong native identity, mismatched result id/project and incomplete outcome', async () => {
  const valid=receipt()
  for(const invalid of [{...valid,entityRef:{...valid.entityRef,entityType:'SITE_SURVEY'}},{...valid,entityRef:{...valid.entityRef,entityId:32}},{...valid,outcome:'ACCEPTED' as const},
    {...valid,references:[{...valid.references[0],value:JSON.stringify({id,projectId:'8',version:5,state:'0',deleted:false})}]}]) {
    vi.mocked(request.post).mockResolvedValue(invalid)
    await expect(api.saveSiteSurveyReceipt({id:id as unknown as number,projectId:7,name:'x',version:4},'key')).rejects.toThrow()
  }
})

it('controlled save and action keep the node client, pinned key/basis and the same public receipt', async () => {
  const execution = { task: { projectId: 7, taskId: 4 } } as api.SiteSurveyExecutionSelection
  controlled.execute.mockResolvedValue({ response: { operationReceipt: receipt() } })
  const data = { id: id as unknown as number, projectId: 7, name: '工勘', version: 4, execution }
  expect(await api.saveSiteSurveyReceipt(data,'controlled-key')).toEqual(receipt())
  expect(controlled.execute).toHaveBeenCalledWith(expect.objectContaining({ operationCode:'SOL.SITE_SURVEY.UPDATE', objectId:id, expectedBusinessVersion:4,key:'controlled-key',input:{id,projectId:7,name:'工勘',version:4} }))
  await api.siteSurveyActionReceipt('confirm',data.id,4,7,'confirm-key',execution)
  expect(controlled.execute).toHaveBeenLastCalledWith(expect.objectContaining({ operationCode:'SOL.SITE_SURVEY.CONFIRM',objectId:id,expectedBusinessVersion:4,key:'confirm-key',input:{} }))
  expect(request.post).not.toHaveBeenCalled()
})
it('a controlled entry refuses to change its actual project before transport', async () => {
  await expect(api.saveSiteSurveyReceipt({projectId:8,name:'wrong',execution:{task:{projectId:7,taskId:4}} as api.SiteSurveyExecutionSelection},'wrong')).rejects.toThrow('BUSINESS_PROJECT_MISMATCH')
  expect(controlled.execute).not.toHaveBeenCalled();expect(request.post).not.toHaveBeenCalled()
})
