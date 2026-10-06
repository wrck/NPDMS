// 统一交付件公共客户端：类型目录、适用配置、要求实例、材料记录、提交台账。
// 文件上传复用统一文件两段式客户端（api/pms/platform/file），本文件只做交付语义调用。
import request from '@/config/axios'
import type { FileBusinessKey } from '@/api/pms/platform/file'

const baseUrl = '/api/v1/pms/delivery'

export interface DeliveryTypeVO {
  id: number
  typeCode: string
  name: string
  category: string
  allowedMediaJson: string
  maxSizeBytes: number
  enabled: boolean
  remark?: string
}

export interface DeliveryConfigVO {
  id: number
  ownerModule: string
  entityType: string
  typeCode: string
  required: boolean
  minimumQuantity: number
  countingUnit: 'MATERIAL' | 'FILE_VERSION' | 'SUBMISSION'
  enabled: boolean
}

export interface DeliveryRequirementVO {
  id: number
  ownerModule: string
  entityType: string
  entityId: string | number
  typeCode: string
  required: boolean
  minimumQuantity: number
  countingUnit: 'MATERIAL' | 'FILE_VERSION' | 'SUBMISSION'
  status: 'OPEN' | 'SATISFIED' | 'CONFIRMED'
  count: number
  configId: number
}

export interface DeliveryMaterialVO {
  id: number
  fileBusinessKey?: FileBusinessKey
  materialKind: 'FILE' | 'BUSINESS_RESULT'
  businessObjectType?: string
  businessObjectId?: string
  businessRevisionNo?: number
  typeCode: string
  fileName?: string
  title?: string
  fileArtifactId?: number
  fileVersionNo?: number
  fileSha256?: string
  fileReferenceId?: number
  sourceKind: string
  status: 'ACTIVE' | 'WITHDRAWN'
  createTime: string
}

export interface DeliverySubmissionVO {
  id: number
  requirementId: number
  requestKey: string
  status: 'CURRENT' | 'SUPERSEDED' | 'WITHDRAWN'
  materialIds: number[]
  createTime: string
}

export interface SubmissionOutcomeVO {
  submissionId: number
  replay: boolean
  requirementId: number
  requirementStatus: string
  count: number
  requestKey: string
}

export interface EntityOwnerParam {
  ownerModule: string
  entityType: string
  entityId: string | number
}

export const getDeliveryTypes = (enabled?: boolean) =>
  request.get<DeliveryTypeVO[]>({ url: `${baseUrl}/types`, params: { enabled }, silentError: true })

export const getMaterialSources = () =>
  request.get<{ code: string; label: string }[]>({ url: `${baseUrl}/material-sources`, silentError: true })

export const listConfigs = (ownerModule: string, entityType: string) =>
  request.get<DeliveryConfigVO[]>({
    url: `${baseUrl}/configs`,
    params: { ownerModule, entityType },
    silentError: true
  })

export const syncRequirements = (data: EntityOwnerParam) =>
  request.post<DeliveryRequirementVO[]>({ url: `${baseUrl}/requirements/sync`, data, silentError: true })

export const listRequirements = (ownerModule: string, entityType: string, entityId: string | number) =>
  request.get<DeliveryRequirementVO[]>({
    url: `${baseUrl}/requirements`,
    params: { ownerModule, entityType, entityId },
    silentError: true
  })

export const confirmRequirement = (requirementId: number) =>
  request.post<DeliveryRequirementVO>({ url: `${baseUrl}/requirements/${requirementId}/confirm`, silentError: true })

export const listMaterials = (
  ownerModule: string,
  entityType: string,
  entityId: string | number,
  typeCode?: string
) =>
  request.get<DeliveryMaterialVO[]>({
    url: `${baseUrl}/materials`,
    params: { ownerModule, entityType, entityId, typeCode },
    silentError: true
  })

export const registerMaterial = (
  data: EntityOwnerParam & { typeCode: string; fileReferenceId: number; title?: string; sourceKind?: string }
) => request.post<DeliveryMaterialVO>({ url: `${baseUrl}/materials`, data, silentError: true })

export const withdrawMaterial = (materialId: number) =>
  request.post<DeliveryMaterialVO>({ url: `${baseUrl}/materials/${materialId}/withdraw`, silentError: true })

export const submitDelivery = (data: { requirementId: number; materialIds: number[]; requestKey: string }) =>
  request.post<SubmissionOutcomeVO>({ url: `${baseUrl}/submissions`, data, silentError: true })

export const withdrawSubmission = (submissionId: number) =>
  request.post<SubmissionOutcomeVO>({ url: `${baseUrl}/submissions/${submissionId}/withdraw`, silentError: true })

export const listSubmissions = (requirementId: number) =>
  request.get<DeliverySubmissionVO[]>({
    url: `${baseUrl}/submissions`,
    params: { requirementId },
    silentError: true
  })

export interface DeliveryCompletionFact {
  requirementId: number
  count: number
  minimumQuantity: number
  satisfied: boolean
  confirmed: boolean
  reason: string
}

export const getCompletion = (requirementId: number) =>
  request.get<DeliveryCompletionFact>({ url: `${baseUrl}/requirements/${requirementId}/completion`, silentError: true })

export const getDeliveryAllowedActions=(ownerModule:string,entityType:string,entityId:string|number):Promise<string[]>=>request.get({url:'/api/v1/pms/delivery/allowed-actions',params:{ownerModule,entityType,entityId}})
