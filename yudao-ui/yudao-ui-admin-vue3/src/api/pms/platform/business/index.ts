import request from '@/config/axios'
import type { BusinessEntityData, BusinessEntityFormData, BusinessOperationReceipt, ModelDetailVO, FieldFilter } from '../businessmodel'
import type { DeliveryRecord, DeliveryScope } from '../businessmodel/delivery'
import { createAccessTicket, type FileBusinessKey } from '../file'

export type BusinessId = string | number
export interface DirectBusinessRevision {
  ref:{entity:{tenantId:BusinessId;ownerModule:string;entityType:string;entityId:BusinessId};revisionId:BusinessId}
  revisionNo:number;state:'DRAFT'|'FROZEN';effective:boolean;version:number;reason?:string;sourceRevisionId?:BusinessId;baseEntityVersion?:number
}
export interface RevisionFieldValue {readable:boolean;value:unknown}
export interface BusinessFieldSetting { code: string; label?: string; displayOrder?: number; listVisible?: boolean; searchable?: boolean; sortable?: boolean }
export interface BusinessFieldConfiguration { version: number; fields: BusinessFieldSetting[] }
export interface BusinessSort { fieldCode: string; direction: 'ASC' | 'DESC' }
export interface BusinessPage { list: BusinessEntityData[]; total: number }

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
    if (!/^[1-9][0-9]*$/.test(String(materialId))) throw new Error('交付件 ID 不合法')
    return `${entity(id)}/deliverables/${materialId}`
  }
  return {
    base,
    model: () => request.get<ModelDetailVO>({ url: `${base}/model`, silentError: true }),
    fieldDefaults: () => request.get<ModelDetailVO>({url:`${base}/field-configuration/defaults`,silentError:true}),
    fieldConfiguration: () => request.get<BusinessFieldConfiguration>({url:`${base}/field-configuration`,silentError:true}),
    saveFieldConfiguration: (configuration: BusinessFieldConfiguration) => request.put<BusinessFieldConfiguration>({url:`${base}/field-configuration`,data:configuration,silentError:true}),
    page: (pageNo: number, pageSize: number, filters: FieldFilter[], sorts?: BusinessSort[]) =>
      request.post<BusinessPage>({ url: `${base}/page`, data: { pageNo, pageSize, filters, ...(sorts?.length ? { sorts } : {}) }, silentError: true }),
    get: (id: BusinessId) => request.get<BusinessEntityData>({ url: entity(id), silentError: true }),
    revisions:(id:BusinessId)=>request.get<DirectBusinessRevision[]>({url:`${entity(id)}/revisions`,params:{limit:100},silentError:true}),
    revisionValues:(id:BusinessId,revisionId:BusinessId)=>request.get<Record<string,RevisionFieldValue>>({url:`${entity(id)}/revisions/${identity(revisionId)}`,silentError:true}),
    revisionForm:(id:BusinessId,revisionId:BusinessId)=>request.get<BusinessEntityFormData>({url:`${entity(id)}/revisions/${identity(revisionId)}/form`,silentError:true}),
    compareRevisions:(id:BusinessId,left:BusinessId,right:BusinessId)=>request.get<Array<{fieldCode:string;before:RevisionFieldValue;after:RevisionFieldValue}>>({url:`${entity(id)}/revisions/compare`,params:{left:identity(left),right:identity(right)},silentError:true}),
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
