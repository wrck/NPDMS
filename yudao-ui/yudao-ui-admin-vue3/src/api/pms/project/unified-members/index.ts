import request from '@/config/axios'
import type { ProjectMemberAssignmentVO } from '@/api/pms/project/projects'

export type ProjectMemberRole = 'PROJECT_MANAGER' | 'SERVICE_MANAGER' | 'TEAM_MEMBER' | 'SALES_REPRESENTATIVE'
export const memberRoleOptions: { value: ProjectMemberRole; label: string; permission: string }[] = [
  { value: 'SERVICE_MANAGER', label: '服务经理', permission: 'pms:project:assign' },
  { value: 'PROJECT_MANAGER', label: '项目经理', permission: 'pms:project:assign' },
  { value: 'SALES_REPRESENTATIVE', label: '销售代表', permission: 'pms:project-team:create' },
  { value: 'TEAM_MEMBER', label: '团队成员', permission: 'pms:project-team:create' }
]
export const logicalMemberRole = (role: string): string =>
  role === 'SERVICE_MANAGER_L1' || role === 'SERVICE_MANAGER_L2' ? 'SERVICE_MANAGER' : role
export interface ServiceMemberScope {
  levelCode: 'L1' | 'L2'
  assignmentType: 'PRIMARY' | 'COLLABORATOR'
  siteId?: number
  departmentId: number
  departmentCode: string
}
export interface MemberRecord extends ProjectMemberAssignmentVO {
  assignmentType?: string | null
  companyName?: string | null
  departmentId?: number | null
  departmentCode?: string | null
  departmentName?: string | null
  siteId?: number | null
  responsibility?: string | null
  remark?: string | null
  changeReason?: string | null
  endReason?: string | null
}
export interface MemberCandidate { id: number; username: string; nickname: string; deptId?: number }
export interface MemberMutation {
  userId: number
  memberRole: ProjectMemberRole
  responsibility?: string
  remark?: string
  reason: string
  primary?: boolean
  scope?: ServiceMemberScope
}
export interface MemberResult {
  projectId: number
  version: number
  assignmentId: number
  userId: number
  memberRole: string
  changed: boolean
}
const base = '/api/v1/pms/projects'
export const getMemberPage = (id: number, params: PageParam & {
  state: 'CURRENT' | 'HISTORY'; role?: string; keyword?: string
}) => request.get<{ list: MemberRecord[]; total: number }>({ url: `${base}/${id}/members`, params })
export const getMemberCandidates = (id: number, params: PageParam & {
  projectRole: ProjectMemberRole; keyword?: string; userId?: number
} & Partial<ServiceMemberScope>) =>
  request.get<{ list: MemberCandidate[]; total: number }>({ url: `${base}/${id}/member-candidates`, params })
export const saveMember = (id: number, assignmentId: number | undefined, data: MemberMutation,
  version: number, key: string) => request[assignmentId === undefined ? 'post' : 'put']<MemberResult>({
    url: `${base}/${id}/members${assignmentId === undefined ? '' : `/${assignmentId}`}`,
    data, headers: { 'If-Match': String(version), 'Idempotency-Key': key }
  })
export const removeMember = (id: number, assignmentId: number, reason: string,
  version: number, key: string, replacementPrimaryUserId?: number) => request.post<MemberResult>({
    url: `${base}/${id}/members/${assignmentId}/actions/remove`, data: { reason, replacementPrimaryUserId },
    headers: { 'If-Match': String(version), 'Idempotency-Key': key }
  })
