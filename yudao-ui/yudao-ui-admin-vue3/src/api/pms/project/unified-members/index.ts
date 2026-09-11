import request from '@/config/axios'
import type { ProjectMemberAssignmentVO } from '@/api/pms/project/projects'

export const MEMBER_ROLE = { PROJECT_MANAGER: 'PROJECT_MANAGER', SERVICE_MANAGER: 'SERVICE_MANAGER',
  TEAM_MEMBER: 'TEAM_MEMBER', SALES_REPRESENTATIVE: 'SALES_REPRESENTATIVE' } as const
export type ProjectMemberRole = typeof MEMBER_ROLE[keyof typeof MEMBER_ROLE]
export const memberRoleOptions: { value: ProjectMemberRole; label: string; permission: string; hint: string }[] = [
  { value: MEMBER_ROLE.SERVICE_MANAGER, label: '服务经理', permission: 'pms:project:assign', hint: '选择服务经理，主子项目使用相同角色。' },
  { value: MEMBER_ROLE.PROJECT_MANAGER, label: '项目经理', permission: 'pms:project:assign', hint: '选择具备当前项目公司资格的系统项目经理。' },
  { value: MEMBER_ROLE.SALES_REPRESENTATIVE, label: '销售代表', permission: 'pms:project-team:create', hint: '选择当前租户的销售代表。' },
  { value: MEMBER_ROLE.TEAM_MEMBER, label: '团队成员', permission: 'pms:project-team:create', hint: '从系统项目经理中选择，不限制公司；加入后在本项目承担团队成员角色。' }
]
export const logicalMemberRole = (role: string): string =>
  role === 'SERVICE_MANAGER_L1' || role === 'SERVICE_MANAGER_L2' ? MEMBER_ROLE.SERVICE_MANAGER : role
export interface ServiceMemberScope {
  levelCode: 'L1' | 'L2'
  assignmentType: 'PRIMARY' | 'COLLABORATOR'
  siteId?: number
  departmentId: number
  departmentCode: string
}
export interface MemberRecord extends ProjectMemberAssignmentVO {
  mobile?: string | null
  email?: string | null
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
export interface MemberCandidate { id: number; username: string; nickname: string; deptId?: number; mobile?: string | null; email?: string | null }
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
