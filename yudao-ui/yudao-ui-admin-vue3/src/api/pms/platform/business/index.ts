import request from '@/config/axios'
import type { BusinessEntityData, BusinessEntityFormData, BusinessOperationReceipt, ModelDetailVO, FieldFilter, BusinessFeatures } from '../businessmodel'
export type { BusinessFeatures } from '../businessmodel'
import type { DeliveryRecord, DeliveryScope } from '../businessmodel/delivery'
import { createAccessTicket, type FileBusinessKey } from '../file'

export type BusinessId = string | number
export interface BusinessCondition {kind:'FIELD'|'DELIVERY'|'ALL'|'ANY'|'NOT'|'MEMBER_ALL'|'MEMBER_ANY'|'COUNT';fieldCode?:string;operator?:FieldFilter['operator'];values?:unknown[];children?:BusinessCondition[]}
export interface BusinessConditionResult {verdict:{satisfied:boolean|null;collectionComplete:boolean;basis:string;diagnostics:string[]};factVersion:string}
export interface DirectBusinessRevision {
  ref:{entity:{tenantId:BusinessId;ownerModule:string;entityType:string;entityId:BusinessId};revisionId:BusinessId}
  revisionNo:number;state:'DRAFT'|'FROZEN';effective:boolean;version:number;reason?:string;sourceRevisionId?:BusinessId;baseEntityVersion?:number
}
export interface RevisionFieldValue {readable:boolean;value:unknown}
export interface BusinessFieldSetting { code: string; label?: string; displayOrder?: number; listVisible?: boolean; searchable?: boolean; sortable?: boolean; formVisible?:boolean; tabVisible?:boolean }
export interface BusinessNamedConfiguration {key:string;name:string;fields:BusinessFieldSetting[];formRevisionId?:BusinessId;features?:BusinessFeatures}
export interface BusinessFieldConfiguration { version: number; fields: BusinessFieldSetting[]; profiles?:BusinessNamedConfiguration[];defaultProfile?:string;features?:BusinessFeatures }
export interface BusinessSort { fieldCode: string; direction: 'ASC' | 'DESC' }
export interface BusinessPage { list: BusinessEntityData[]; total: number }
export interface ProjectBusinessRecordFormData extends BusinessEntityFormData { entity: BusinessEntityData }
export interface BusinessPageInitialRequest { projectId: BusinessId; initialEntityId?: BusinessId; configurationName?: string; bindingParameters?: string }
export interface BusinessPageInitialData {
  request: BusinessPageInitialRequest
  model: ModelDetailVO
  configuration: BusinessFieldConfiguration
  pageSize: number
  records: BusinessPage
  forms: ProjectBusinessRecordFormData[]
  relatedPages: BusinessPageInitialData[]
  context: Record<string, unknown>
}
export interface BusinessPageOpening { view: DirectBusinessView; data: BusinessPageInitialData }
export interface BusinessApprovalDefinition {id:string;key:string;name:string;version:number;formConf?:string;formFields:string[];available:boolean;unavailableReason?:string}
export interface BusinessApprovalPreview {nodes:Array<{id:string;name:string;selectApprovers:boolean}>;fieldPermissions:Record<string,string>}
export interface BusinessApprovalAttempt {id:BusinessId;definitionId:string;instanceId:string;status:'PENDING'|'APPROVED'|'REJECTED'|'WITHDRAWN';submittedVersion:number;currentContent:boolean;creator:string;submittedAt:string|number;decidedAt?:string|number;conclusion?:string;previousAttemptId?:BusinessId}
export interface BusinessApprovalStart {definitionId:string;version:number;idempotencyKey:string;variables:Record<string,unknown>;selectedApprovers:Record<string,BusinessId[]>}
export interface BusinessApprovalDetail {attempt:BusinessApprovalAttempt;process:{name:string;submittedValues:Record<string,unknown>;opinions:Array<{userId:string;action:string;comment:string;time:string|number}>}}

/** One client factory for every inherited business Controller; no catalog/operation-dispatch URL. */
export const createProjectBusinessApi = (prefix: string) => {
  const base = prefix.replace(/\/$/, '')
  if (!/^\/api\/v1\/pms\/[A-Za-z0-9_/-]+$/.test(base)) throw new Error('业务 API 路径不合法')
  const identity = (id: BusinessId) => {
    if (typeof id === 'number' && !Number.isSafeInteger(id) || !/^[1-9][0-9]*$/.test(String(id))) throw new Error('业务实体 ID 不合法')
    return String(id)
  }
  const entity=(id:BusinessId)=>`${base}/${identity(id)}`
  const material = (id: BusinessId, materialId: BusinessId) => {
    return `${entity(id)}/deliverables/${identity(materialId)}`
  }
  return {
    base,
    model: (configurationName?:string) => request.get<ModelDetailVO>({ url: `${base}/model`, params:configurationName==null?undefined:{configurationName},silentError: true }),
    fieldDefaults: () => request.get<ModelDetailVO>({url:`${base}/field-configuration/defaults`,silentError:true}),
    fieldConfiguration: () => request.get<BusinessFieldConfiguration>({url:`${base}/field-configuration`,silentError:true}),
    saveFieldConfiguration: (configuration: BusinessFieldConfiguration) => request.put<BusinessFieldConfiguration>({url:`${base}/field-configuration`,data:configuration,silentError:true}),
    page: (pageNo: number, pageSize: number, filters: FieldFilter[], sorts?: BusinessSort[], projectId?: BusinessId,configurationName?:string) =>
      request.post<BusinessPage>({ url: `${base}/page`, data: { pageNo, pageSize, filters, ...(sorts?.length ? { sorts } : {}), ...(projectId == null ? {} : { projectId: identity(projectId) }),...(configurationName==null?{}:{configurationName}) }, silentError: true }),
    evaluate:(id:BusinessId,condition:BusinessCondition)=>request.post<BusinessConditionResult>({url:`${entity(id)}/evaluate`,data:condition,silentError:true}),
    businessCompletion:(id:BusinessId)=>request.get<BusinessConditionResult|null>({url:`${entity(id)}/completion`,silentError:true}),
    get: (id: BusinessId) => request.get<BusinessEntityData>({ url: entity(id), silentError: true }),
    approvalDefinitions:(id:BusinessId)=>request.get<BusinessApprovalDefinition[]>({url:`${entity(id)}/approvals/definitions`,silentError:true}),
    approvalPreview:(id:BusinessId,definitionId:string,variables:Record<string,unknown>)=>request.post<BusinessApprovalPreview>({url:`${entity(id)}/approvals/preview`,data:{definitionId,variables},silentError:true}),
    approvals:(id:BusinessId)=>request.get<BusinessApprovalAttempt[]>({url:`${entity(id)}/approvals`,silentError:true}),
    approval:(id:BusinessId,attemptId:BusinessId)=>request.get<BusinessApprovalDetail>({url:`${entity(id)}/approvals/${identity(attemptId)}`,silentError:true}),
    startApproval:(id:BusinessId,body:BusinessApprovalStart)=>request.post<BusinessApprovalAttempt>({url:`${entity(id)}/approvals`,data:body,silentError:true}),
    cancelApproval:(id:BusinessId,attemptId:BusinessId,reason:string)=>request.post<BusinessApprovalAttempt>({url:`${entity(id)}/approvals/${identity(attemptId)}/cancel`,data:{reason},silentError:true}),
    revisions:(id:BusinessId,beforeId?:BusinessId)=>request.get<DirectBusinessRevision[]>({url:`${entity(id)}/revisions`,params:{limit:100,...(beforeId===undefined?{}:{beforeId:identity(beforeId)})},silentError:true}),
    revisionValues:(id:BusinessId,revisionId:BusinessId)=>request.get<Record<string,RevisionFieldValue>>({url:`${entity(id)}/revisions/${identity(revisionId)}`,silentError:true}),
    revisionForm:(id:BusinessId,revisionId:BusinessId)=>request.get<BusinessEntityFormData>({url:`${entity(id)}/revisions/${identity(revisionId)}/form`,silentError:true}),
    compareRevisions:(id:BusinessId,left:BusinessId,right:BusinessId)=>request.get<Array<{fieldCode:string;before:RevisionFieldValue;after:RevisionFieldValue}>>({url:`${entity(id)}/revisions/compare`,params:{left:identity(left),right:identity(right)},silentError:true}),
    formOptions:(projectId:BusinessId)=>request.get<Array<{name:string;layout:NonNullable<BusinessEntityFormData['layout']>}>>({url:`${base}/form-options`,params:{projectId:identity(projectId)},silentError:true}),
    formDefaults:(projectId:BusinessId)=>request.get<BusinessEntityFormData>({url:`${base}/form-defaults`,params:{projectId:identity(projectId)},silentError:true}),
    form: (id: BusinessId) => request.get<BusinessEntityFormData>({url:`${entity(id)}/form`,silentError:true}),
    create: (values: Record<string, unknown>, key: string) =>
      request.post<BusinessOperationReceipt>({ url: base, data: { values, idempotencyKey: key }, silentError: true }),
    update: (id: BusinessId, values: Record<string, unknown>, version: number, key: string) =>
      request.put<BusinessOperationReceipt>({ url: entity(id), data: { values, version, idempotencyKey: key }, silentError: true }),
    remove: (id: BusinessId, version: number, key: string) =>
      request.delete<BusinessOperationReceipt>({ url: entity(id), params: { version }, headers: { 'Idempotency-Key': key }, silentError: true }),
    action: (operation: string, id: BusinessId, version: number, key: string, values: Record<string, unknown> = {}) => {
      if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(operation) || ['create','save','delete'].includes(operation)) throw new Error('业务操作路径不合法')
      return request.post<BusinessOperationReceipt>({url:`${entity(id)}/${operation}`,data:{...values,version,idempotencyKey:key},silentError:true})
    },
    receipt: (operation: string, key: string) =>
      request.get<BusinessOperationReceipt | null>({ url: `${base}/receipts/${encodeURIComponent(key)}`, params: { operation }, silentError: true }),
    deliveryContext: (id: BusinessId) => request.get<Omit<DeliveryScope, 'deliverableType'>>({ url: `${entity(id)}/deliverables/context`, silentError: true }),
    upload: (scope: DeliveryScope, file: File, key: string) => {
      const data = new FormData()
      Object.entries(scope).forEach(([name, value]) => data.append(name, String(value)))
      data.append('file', file)
      return request.post<DeliveryRecord>({ url: `${entity(scope.businessEntityKey)}/deliverables`, data,
        headersType: 'multipart/form-data', headers: { 'Idempotency-Key': key }, silentError: true })
    },
    deliveries: (id: BusinessId, deliverableType?: string, pageNo = 1) =>
      request.get<{ list: DeliveryRecord[]; total: number }>({ url: `${entity(id)}/deliverables`, params: { deliverableType, pageNo, pageSize: 20 }, silentError: true }),
    deliveryHistory: (id: BusinessId, deliverableType?: string, pageNo = 1) =>
      request.get<{ list: DeliveryRecord[]; total: number }>({ url: `${entity(id)}/deliverables/history`, params: { deliverableType, pageNo, pageSize: 20 }, silentError: true }),
    completion: (scope: DeliveryScope) => request.get<{ completed: boolean; latest?: DeliveryRecord }>({ url: `${entity(scope.businessEntityKey)}/deliverables/completion`, params: scope, silentError: true }),
    editDelivery: (id: BusinessId, row: DeliveryRecord, title: string) =>
      request.put<DeliveryRecord>({ url: material(id, row.id), data: { version: row.version, title }, silentError: true }),
    removeDelivery: (id: BusinessId, row: DeliveryRecord) =>
      request.delete<boolean>({ url: material(id, row.id), params: { version: row.version }, silentError: true }),
    downloadDelivery: async (id: BusinessId, row: DeliveryRecord) => {
      const key = await request.get<FileBusinessKey>({ url: `${material(id, row.id)}/file`, silentError: true })
      return createAccessTicket(row.fileArtifactId, row.fileVersionNo, 'DOWNLOAD', { ownerContext:key.ownerContext, objectType:key.objectType, objectId:key.objectId, purposeCode:key.purposeCode, referenceKey:key.referenceKey })
    }
  }
}
export type ProjectBusinessApi = ReturnType<typeof createProjectBusinessApi>

export interface DirectBusinessView {ownerModule:string;entityType:string;stableCode:string;apiBase:string}
export const getDirectBusinessView = (code:string) => {
  if(!/^[A-Za-z][A-Za-z0-9_-]{0,127}$/.test(code))throw new Error('业务类型不合法')
  return request.get<DirectBusinessView>({url:`/api/v1/pms/business-defaults/${code}/view`,silentError:true})
}
