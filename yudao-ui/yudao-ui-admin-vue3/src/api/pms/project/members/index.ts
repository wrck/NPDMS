import request from '@/config/axios'

/** PM-01：主责与完整角色成员分开表达。 */
export interface ProjectManagerMember {
  assignmentId: number
  userId: number
  name: string
  effectiveFrom?: number
}
export interface ProjectManagers {
  projectId: number
  version: number
  primaryUserId: number | null
  assignmentStatus: string
  changed: boolean
  members: ProjectManagerMember[]
}
export interface ManagerCandidate {
  userId: number
  username: string
  nickname: string
}
export interface ServiceManagerSelection {
  levelCode: 'L1' | 'L2'
  managerId: number
  siteId?: number
  assignmentType: 'PRIMARY' | 'COLLABORATOR'
  departmentId: number
  departmentCode: string
}
export interface MemberUpdate {
  addUserIds: number[]
  removeUserIds: number[]
  primaryUserId?: number
  reason: string
  serviceManager?: ServiceManagerSelection
}
const base = '/api/v1/pms/projects'
export const getProjectManagers = (id: number) =>
  request.get<ProjectManagers>({ url: `${base}/${id}/project-managers` })
export const getManagerCandidates = (id: number, keyword: string, pageNo = 1) =>
  request.get<{ list: ManagerCandidate[]; total: number }>({
    url: `${base}/${id}/project-manager-candidates`,
    params: { keyword, pageNo, pageSize: 20 }
  })
export const updateMembers = (id: number, data: MemberUpdate, version: number, key: string) =>
  request.post<{ projectManagers: ProjectManagers; serviceManager?: { assignmentId: number } }>({
    url: `${base}/${id}/actions/update-members`,
    data,
    headers: { 'If-Match': String(version), 'Idempotency-Key': key }
  })
