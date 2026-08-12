import request from '@/config/axios'

export interface RequirementVO {
  id?: number
  projectId: number
  code: string
  name: string
  requirementType: string
  background?: string
  topology?: string
  transmission?: string
  traffic?: string
  business?: string
  ipPlan?: string
  redundancy?: string
  protection?: string
  oAndM?: string
  logRetention?: string
  interfaceContent?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/eng-requirement'

export const getRequirementPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getRequirement = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createRequirement = (data: RequirementVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateRequirement = (data: RequirementVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteRequirement = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitRequirement = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const markEffectiveRequirement = (id: number) =>
  request.put({ url: `${baseUrl}/mark-effective`, params: { id } })
export const archiveRequirement = (id: number) =>
  request.put({ url: `${baseUrl}/archive`, params: { id } })
