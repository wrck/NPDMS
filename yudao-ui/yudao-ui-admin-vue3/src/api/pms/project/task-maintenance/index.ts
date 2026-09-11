import request from '@/config/axios'
import type { TaskCommandResult, TaskCreateCommand } from '@/api/pms/project/task-workbench'
export type TaskRole = 'RESPONSIBLE' | 'EXECUTOR'
export const TASK_ROLE_OPTIONS: { value: TaskRole; label: string }[] = [
  { value: 'RESPONSIBLE', label: '负责人' }, { value: 'EXECUTOR', label: '执行人' }
]
export interface TaskRoleEntry { role: TaskRole; userId: number; name: string; effectiveFrom: string | number; effectiveTo?: string | number; reason?: string }
export interface TaskMaintenanceView {
  taskId: number | string; version: number; description: { description?: string; descriptionFormat: 'PLAIN' | 'HTML' }
  descriptionLimit: number; currentRoles: TaskRoleEntry[]; canAssign: boolean; canEdit: boolean
}
const task = (id: number | string) => `/api/v1/pms/project-tasks/${id}`
export const createNativeTask = (projectId: number, input: TaskCreateCommand, key: string) => {
  const { description, ...metadata } = input
  return request.post<TaskCommandResult>({ url: `/api/v1/pms/projects/${projectId}/tasks/native`,
    data: { task: metadata, descriptionHtml: description || '' }, headers: { 'Idempotency-Key': key } })
}
export const getMaintenance = (id: number | string) => request.get<TaskMaintenanceView>({ url: `${task(id)}/maintenance` })
type MemberPage = { list: { userId: number; name: string }[]; total: number }
const memberRequests = new Map<string, Promise<MemberPage>>()
export const getMembers = (projectId: number, params: PageParam & { keyword?: string }) => {
  // 负责人、执行人筛选共享同一候选来源；只合并进行中的同参数请求，不缓存成员资格。
  const key = JSON.stringify([projectId, params.pageNo, params.pageSize, params.keyword || ''])
  const existing = memberRequests.get(key)
  if (existing) return existing
  const pending = request.get<MemberPage>({ url: `/api/v1/pms/projects/${projectId}/task-members`, params }).finally(() => memberRequests.delete(key))
  memberRequests.set(key, pending)
  return pending
}
export const getRoleHistory = (id: number | string, role: TaskRole, pageNo: number) =>
  request.get<{ list: TaskRoleEntry[]; hasMore: boolean }>({ url: `${task(id)}/role-history`, params: { role, pageNo, pageSize: 20 } })
export const changeRole = (id: number | string, version: number, body: { role: TaskRole; userId: number; reason: string }, key: string) =>
  request.put<TaskCommandResult>({ url: `${task(id)}/roles`, data: body, headers: { 'If-Match': String(version), 'Idempotency-Key': key } })
export const saveDescription = (id: number | string, version: number, html: string, key: string) =>
  request.put<TaskCommandResult>({ url: `${task(id)}/description`, data: { html }, headers: { 'If-Match': String(version), 'Idempotency-Key': key } })
