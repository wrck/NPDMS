import request from '@/config/axios'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'

export interface SiteSurveyVO {
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
  createTime?: Date
}

const baseUrl = '/pms/eng-site-survey'

export const getSiteSurveyPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getSiteSurvey = (id: number) => request.get({ url: `${baseUrl}/get`, params: { id } })
