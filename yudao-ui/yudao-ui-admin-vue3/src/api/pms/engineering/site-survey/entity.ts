import request from '@/config/axios'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import type { ProjectBusinessExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'
import { selectionClient } from '@/components/BusinessView/operationClient'

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
  code: string
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
export const createSiteSurvey = async (data: SiteSurveyVO) => {
  const client = selectionClient(data.execution)
  if (!client) return request.post({ url: `${baseUrl}/create`, data })
  const { execution: _execution, ...input } = data
  const result = await client.execute({ operationCode: 'SOL.SITE_SURVEY.CREATE', input })
  return (result.response as { id: number }).id
}
export const updateSiteSurvey = async (data: SiteSurveyVO) => {
  const client = selectionClient(data.execution)
  if (!client) return request.put({ url: `${baseUrl}/update`, data })
  const { execution: _execution, ...input } = data
  await client.execute({ operationCode: 'SOL.SITE_SURVEY.UPDATE', objectId: data.id, expectedBusinessVersion: data.version, input })
  return true
}
const action = async (name: 'delete' | 'confirm' | 'reject' | 'archive', id: number, execution?: SiteSurveyExecutionSelection) => {
  const client = selectionClient(execution)
  if (!client) return name === 'delete'
    ? request.delete({ url: `${baseUrl}/delete`, params: { id }, data: execution })
    : request.put({ url: `${baseUrl}/${name}`, params: { id }, data: execution })
  await client.execute({ operationCode: `SOL.SITE_SURVEY.${name.toUpperCase()}`, objectId: id, input: {},
    expectedBusinessVersion: async () => {
      const row = await getSiteSurvey(id)
      if (String(row.id) !== String(id) || String(row.projectId) !== String(client.target.projectId) || row.version == null)
        throw new Error('BUSINESS_OBJECT_MISMATCH')
      return row.version
    } })
  return true
}
export const deleteSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) => action('delete', id, execution)
export const confirmSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) => action('confirm', id, execution)
export const rejectSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) => action('reject', id, execution)
export const archiveSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) => action('archive', id, execution)
