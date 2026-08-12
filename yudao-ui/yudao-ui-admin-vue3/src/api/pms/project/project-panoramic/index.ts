import request from '@/config/axios'

export interface ProjectPanoramicVO {
  id?: number
  code?: string
  name?: string
  category?: string
  projectType?: string
  majorProjectFlag?: boolean
  managerUserId?: number
  status?: number
  createTime?: Date
  customerId?: number
  customerCode?: string
  customerName?: string
  phaseTotalCount?: number
  phaseNotStartedCount?: number
  phaseInProgressCount?: number
  phaseCompletedCount?: number
  phaseSkippedCount?: number
  taskTotalCount?: number
  taskCompletedCount?: number
  taskInProgressCount?: number
  taskBlockedCount?: number
  riskTotalCount?: number
  riskHighCount?: number
  riskMediumCount?: number
  riskLowCount?: number
  riskIdentifiedCount?: number
  riskInProgressCount?: number
  riskClosedCount?: number
  riskOccurredCount?: number
  teamMembers?: any[]
}

export interface ProjectProgressVO {
  projectId?: number
  phaseProgress?: number
  taskProgress?: number
  overallProgress?: number
  phaseTotalCount?: number
  phaseCompletedCount?: number
  taskTotalCount?: number
  taskCompletedCount?: number
}

const baseUrl = '/pms/project-panoramic'

export const getProjectPanoramic = (projectId: number) =>
  request.get({ url: `${baseUrl}/panoramic`, params: { projectId } })
export const getProjectProgress = (projectId: number) =>
  request.get({ url: `${baseUrl}/progress`, params: { projectId } })
