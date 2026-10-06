import request from '@/config/axios'
import { siteSurveyOperationInput } from './operationInput'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import type { ProjectBusinessExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'
import { selectionClient, captureOperationResponse } from '@/components/BusinessView/operationClient'
import { service } from '@/config/axios/service'
import { siteSurveyResult } from './operationResults'
import { newIdempotencyKey, type BusinessOperationReceipt, type OperationExecuteRequest } from '@/api/pms/platform/businessmodel'
import { isBusinessViewId, sameBusinessViewId, legacyOwnerId } from '@/api/pms/platform/business-view/ids'

export type SiteSurveyExecutionSelection = ProjectBusinessExecutionSelection
export interface SiteSurveyFormSchemaVO {
  revisionId: number
  revisionVersion: number
  formConfJson: JsonObject
  formRulesJson: JsonObject[]
  fieldBindings: Record<string, string>
  fieldCatalog: { code: string; type: string; required: boolean }[]
}
export const getDefaultFormSchema = () => request.get<SiteSurveyFormSchemaVO>({ url: '/api/v1/pms/site-surveys/default-form-schema' })
export interface SiteSurveyVO {
  execution?: SiteSurveyExecutionSelection
  projectEndDateVersion?: number
  projectEndDateChanged?: boolean
  outsourceRequired?: boolean
  outsourceRequestId?: number
  id?: number
  projectId: number
  code?: string
  name: string
  surveyDate?: string
  surveyorUserId?: number
  location?: string
  locationMaintenance?: LocationMaintainRequest
  addressId?: number
  addressVersion?: number
  siteId?: number
  siteVersion?: number
  siteLocationId?: number
  siteLocationVersion?: number
  locationResolutionStatus?: 'RESOLVED' | 'UNRESOLVED'
  addressSnapshot?: string
  locationSnapshot?: string
  powerSupply?: string
  cabinet?: string
  networkPort?: string
  fiber?: string
  module?: string
  cable?: string
  ground?: string
  constructionResource?: string
  conclusion?: string
  status?: number
  remark?: string
  version?: number
  extensionDefinitionRevisionId?: number
  formRevisionId?: number
  formRevisionVersion?: number
  businessValues?: Record<string, any>
  extensionValues?: Record<string, any>
  fieldBindings?: Record<string, string>
  fieldCatalog?: { code: string; type: string; required: boolean }[]
  createTime?: Date
}
const baseUrl = '/api/v1/pms/site-surveys'
export const getSiteSurveyPage = (params: PmsProjectPageParam) => request.get({ url: `${baseUrl}/page`, params })
export const getSiteSurvey = (id: number) => request.get<SiteSurveyVO>({ url: `${baseUrl}/get`, params: { id } })
export const getFormSchema = (revisionId: number, revisionVersion: number) => request.get({ url: `${baseUrl}/form-schema`, params: { revisionId, revisionVersion } })
export interface SiteSurveyCommandResult {
  id: string | number
  projectId: string | number
  version: number
  state: string
  deleted: boolean
}
type Action = 'delete' | 'confirm' | 'reject' | 'archive'
const actionCode = { delete: 'DELETE', confirm: 'CONFIRM', reject: 'REJECT', archive: 'ARCHIVE' } as const

// The same public endpoint retains parsed refusals before the global interceptor discards their codes.
const submitIndependentSurvey = (operation: string, id: number | undefined, data: OperationExecuteRequest) =>
  request.post<BusinessOperationReceipt>({ url: `/api/v1/pms/business-models/SOL/siteSurvey/operations/${operation}`,
    params: id == null ? undefined : { entityId: id }, data, silentError: true,
    transformResponse: [...(Array.isArray(service.defaults.transformResponse) ? service.defaults.transformResponse
      : service.defaults.transformResponse ? [service.defaults.transformResponse] : []), captureOperationResponse] })

export const surveyReceiptResult = (receipt: BusinessOperationReceipt, id?: string | number, projectId?: string | number): SiteSurveyCommandResult => {
  const ref = receipt.entityRef
  if (!['SAVED', 'EFFECTED'].includes(receipt.outcome) || ref?.ownerModule !== 'SOL' || ref.entityType !== 'siteSurvey'
    || !isBusinessViewId(ref.entityId) || id != null && !sameBusinessViewId(ref.entityId, id)
    || !Number.isSafeInteger(receipt.newConcurrencyBasis) || receipt.newConcurrencyBasis! < 0)
    throw new Error(receipt.failureReason || '工勘回执身份或并发依据无效')
  const reference = receipt.references?.find(item => item.kind === 'COMMAND' && item.ownerModule === 'SOL')
  if (!reference) throw new Error('工勘回执缺少业务结果')
  const result = JSON.parse(reference.value) as SiteSurveyCommandResult
  if (!sameBusinessViewId(result.id, ref.entityId) || !isBusinessViewId(result.projectId)
    || projectId != null && !sameBusinessViewId(result.projectId, projectId)
    || result.version !== receipt.newConcurrencyBasis || typeof result.state !== 'string' || typeof result.deleted !== 'boolean')
    throw new Error('工勘回执结果与真实对象不一致')
  return result
}
const savedValues = (data: SiteSurveyVO) => {
  const { id: _id, version: _version, code: _code, status: _status, ...values } = siteSurveyOperationInput(data)
  return values
}
export const saveSiteSurveyReceipt = async (data: SiteSurveyVO, key: string): Promise<BusinessOperationReceipt> => {
  const create = data.id == null
  const client = selectionClient(data.execution)
  let receipt: BusinessOperationReceipt
  if (client) {
    if (!sameBusinessViewId(data.projectId, client.target.projectId)) throw new Error('BUSINESS_PROJECT_MISMATCH')
    const result = await client.execute({ operationCode: create ? 'SOL.SITE_SURVEY.CREATE' : 'SOL.SITE_SURVEY.UPDATE',
      objectId: create ? undefined : data.id, expectedBusinessVersion: create ? undefined : data.version,
      input: siteSurveyOperationInput(data), key, validateResult: siteSurveyResult(create ? 'CREATE' : 'UPDATE') })
    receipt = (result.response as { operationReceipt: BusinessOperationReceipt }).operationReceipt
  } else {
    receipt = await submitIndependentSurvey(create ? 'create' : 'save', data.id, {
      idempotencyKey: key, concurrencyBasis: create ? undefined : data.version,
      entryKind: 'INDEPENDENT', input: { values: savedValues(data), ...(data.execution ? { execution: data.execution } : {}) }
    })
  }
  surveyReceiptResult(receipt, data.id, data.projectId)
  return receipt
}
// Compatibility callers keep their result shape; active forms use the receipt variants with a pinned intent key.
export const createSiteSurvey = async (data: SiteSurveyVO, key: string = newIdempotencyKey()) => {
  const { id: _id, version: _version, ...create } = data
  return legacyOwnerId(surveyReceiptResult(await saveSiteSurveyReceipt(create, key), undefined, data.projectId).id)
}
export const updateSiteSurvey = async (data: SiteSurveyVO, key: string = newIdempotencyKey()) => {
  if (data.id == null || data.version == null) throw new Error('BUSINESS_OBJECT_VERSION_REQUIRED')
  await saveSiteSurveyReceipt(data, key)
  return true
}
export const siteSurveyActionReceipt = async (name: Action, id: number, version: number, projectId: number,
  key: string, execution?: SiteSurveyExecutionSelection): Promise<BusinessOperationReceipt> => {
  const client = selectionClient(execution)
  let receipt: BusinessOperationReceipt
  if (client) {
    if (!sameBusinessViewId(projectId, client.target.projectId)) throw new Error('BUSINESS_PROJECT_MISMATCH')
    const result = await client.execute({ operationCode: `SOL.SITE_SURVEY.${name.toUpperCase()}`, objectId: id,
      expectedBusinessVersion: version, input: {}, key, validateResult: siteSurveyResult(actionCode[name]) })
    receipt = (result.response as { operationReceipt: BusinessOperationReceipt }).operationReceipt
  } else {
    receipt = await submitIndependentSurvey(name, id, {
      idempotencyKey: key, concurrencyBasis: version, entryKind: 'INDEPENDENT',
      input: execution ? { execution } : {}
    })
  }
  const result = surveyReceiptResult(receipt, id, projectId)
  if (result.deleted !== (name === 'delete')) throw new Error('工勘回执删除状态无效')
  return receipt
}
const action = async (name: Action, id: number, execution?: SiteSurveyExecutionSelection) => {
  const row = await getSiteSurvey(id)
  if (!sameBusinessViewId(row.id, id) || row.version == null) throw new Error('BUSINESS_OBJECT_MISMATCH')
  await siteSurveyActionReceipt(name, id, row.version, row.projectId, newIdempotencyKey(), execution)
  return true
}
export const deleteSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) => action('delete', id, execution)
export const confirmSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) => action('confirm', id, execution)
export const rejectSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) => action('reject', id, execution)
export const archiveSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) => action('archive', id, execution)
