import request from '@/config/axios'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import type { ProjectBusinessExecutionSelection } from '@/api/pms/project/projects/nodeExecutions'

export type SiteSurveyExecutionSelection = ProjectBusinessExecutionSelection

export interface SiteSurveyFormSchemaVO {
  revisionId: number
  revisionVersion: number
  formConfJson: JsonObject
  formRulesJson: JsonObject[]
}

export const getDefaultFormSchema = () =>
  request.get<SiteSurveyFormSchemaVO>({ url: '/api/v1/pms/site-surveys/default-form-schema' })

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
  formRevisionId?: number
  formRevisionVersion?: number
  formExtraValues?: Record<string, any>
  createTime?: Date
}

const baseUrl = '/pms/sol-site-survey'

export const getSiteSurveyPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getSiteSurvey = (id: number) => request.get({ url: `${baseUrl}/get`, params: { id } })
export const getFormSchema = (revisionId: number, revisionVersion: number) =>
  request.get({
    url: '/api/v1/pms/site-surveys/form-schema',
    params: { revisionId, revisionVersion }
  })

export const createSiteSurvey = (data: SiteSurveyVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateSiteSurvey = (data: SiteSurveyVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id }, data: execution })
export const confirmSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) =>
  request.put({ url: `${baseUrl}/confirm`, params: { id }, data: execution })
export const rejectSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) =>
  request.put({ url: `${baseUrl}/reject`, params: { id }, data: execution })
export const archiveSiteSurvey = (id: number, execution?: SiteSurveyExecutionSelection) =>
  request.put({ url: `${baseUrl}/archive`, params: { id }, data: execution })
