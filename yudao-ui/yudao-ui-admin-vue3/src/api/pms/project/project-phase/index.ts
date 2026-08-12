import request from '@/config/axios'

export interface ProjectPhaseVO {
  id?: number
  projectId?: number
  templateId?: number
  name: string
  code: string
  sort?: number
  status?: number
  suggestedStartTime?: Date
  suggestedEndTime?: Date
  planStartTime?: Date
  planEndTime?: Date
  actualStartTime?: Date
  actualEndTime?: Date
  deviationReason?: string
  entryCriteria?: string
  exitCriteria?: string
  responsibleRole?: string
  responsibleUserId?: number
  version?: number
  createTime?: Date
}

export interface ProjectPhaseCompleteReqVO {
  phaseId: number
  gateEvidence?: string
  version?: number
}

const baseUrl = '/pms/project-phase'

export const getProjectPhasePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getProjectPhase = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createProjectPhase = (data: ProjectPhaseVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateProjectPhase = (data: ProjectPhaseVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteProjectPhase = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getProjectPhaseListByProjectId = (projectId: number) =>
  request.get({ url: `${baseUrl}/list-by-project`, params: { projectId } })
export const instantiatePhaseFromTemplate = (projectId: number, templateId: number) =>
  request.post({
    url: `${baseUrl}/instantiate-from-template`,
    params: { projectId, templateId }
  })
export const validatePhaseSequence = (phaseId: number) =>
  request.get({ url: `${baseUrl}/validate-sequence`, params: { phaseId } })
export const checkPhaseCompletionGate = (phaseId: number) =>
  request.post({ url: `${baseUrl}/check-gate`, params: { phaseId } })
export const completeProjectPhase = (data: ProjectPhaseCompleteReqVO) =>
  request.put({ url: `${baseUrl}/complete`, data })
export const getOverduePhases = () => request.get({ url: `${baseUrl}/overdue` })
export const getUpcomingPhases = (daysWithin = 7) =>
  request.get({ url: `${baseUrl}/upcoming`, params: { daysWithin } })
