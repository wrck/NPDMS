import request from '@/config/axios'

export interface SolutionVO {
  id?: number
  projectId: number
  code: string
  name: string
  solutionType?: string
  background?: string
  target?: string
  team?: string
  inventory?: string
  plan?: string
  topology?: string
  interfacePlan?: string
  ipPlan?: string
  versionLabel?: string
  script?: string
  quality?: string
  risk?: string
  oAndM?: string
  reviewLevel?: number
  status?: number
  approvedBy?: number
  approvedTime?: Date
  approvalOpinion?: string
  baselineVersion?: number
  remark?: string
  version?: number
  createTime?: Date
}

export interface SolutionApproveVO {
  id: number
  approvalOpinion?: string
  version?: number
}

export interface SolutionGenerateDraftVO {
  projectId: number
  solutionCode: string
  solutionName?: string
}

const baseUrl = '/pms/eng-solution'

export const getSolutionPage = (params: PmsProjectPageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getSolution = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createSolution = (data: SolutionVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateSolution = (data: SolutionVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteSolution = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const submitSolution = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const startReviewSolution = (id: number) =>
  request.put({ url: `${baseUrl}/start-review`, params: { id } })
export const approveSolution = (data: SolutionApproveVO) =>
  request.put({ url: `${baseUrl}/approve`, data })
export const rejectSolution = (data: SolutionApproveVO) =>
  request.put({ url: `${baseUrl}/reject`, data })
export const withdrawSolution = (id: number) =>
  request.put({ url: `${baseUrl}/withdraw`, params: { id } })
export const terminateSolution = (id: number) =>
  request.put({ url: `${baseUrl}/terminate`, params: { id } })
export const generateDraft = (data: SolutionGenerateDraftVO) =>
  request.post({ url: `${baseUrl}/generate-draft`, data })
