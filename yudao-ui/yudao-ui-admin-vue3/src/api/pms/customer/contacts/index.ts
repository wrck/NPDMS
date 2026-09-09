import request from '@/config/axios'
import type { CustomerContactVO } from '@/api/pms/project/customer-contact'

export interface ContactVO extends CustomerContactVO {
  customerName?: string
  projectId?: number
  customerContactId?: number
  sourceContactId?: number
  roleCode?: string
  expectedProjectVersion?: number
  confirmNoPrimary?: boolean
}
export interface ProjectContactContext {
  project: { projectId: number; customerId?: number; projectVersion: number; lifecycleStatus: string; canManage: boolean; canViewHistory: boolean }
  customerName?: string
  sensitiveRead: boolean
  customerStatus?: 'ENABLED' | 'DISABLED' | 'DELETED'
}
const master = '/api/v1/pms/customer-contacts'
export interface ContactHistoryVO { id: number; projectRelationId: number; actionCode: string; beforeValues?: string; afterValues?: string; actorUserId: number; occurredAt: string; restorable: boolean }
const project = (projectId: number) => `/api/v1/pms/projects/${projectId}/customer-contacts`
export const getMasterPage = (params: PageParam & { customerId?: number; name?: string; status?: number }) =>
  request.get<{ list: ContactVO[]; total: number }>({ url: master, params })
export const createMaster = (data: ContactVO, key: string) => request.post<number>({ url: master, data, headers: { 'Idempotency-Key': key } })
export const updateMaster = (data: ContactVO) => request.put({ url: `${master}/${data.id}`, data })
export const deleteMaster = (data: ContactVO) => request.delete({ url: `${master}/${data.id}`, params: { customerId: data.customerId }, headers: { 'If-Match': String(data.version) } })
export const getProjectContext = (id: number) => request.get<ProjectContactContext>({ url: `${project(id)}/context` })
export const getProjectPage = (id: number, params: PageParam & { status?: number; name?: string }) => request.get<{ list: ContactVO[]; total: number }>({ url: project(id), params })
export const getProjectSources = (id: number, params: PageParam) => request.get<{ list: ContactVO[]; total: number }>({ url: `${project(id)}/sources`, params })
export const getProjectHistory = (id: number, params: PageParam) => request.get<{ list: ContactHistoryVO[]; total: number }>({ url: `${project(id)}/history`, params })
export const importDefaults = (id: number, expectedProjectVersion: number) => request.post<number>({ url: `${project(id)}/actions/import-defaults`, data: { expectedProjectVersion } })
export const associateCustomer = (id: number, customerId: number, expectedProjectVersion: number) => request.post({ url: `${project(id)}/actions/associate-customer`, data: { customerId, expectedProjectVersion } })
export const createProjectContact = (id: number, data: ContactVO, key: string) => request.post<number>({ url: project(id), data, headers: { 'Idempotency-Key': key } })
export const updateProjectContact = (id: number, data: ContactVO) => request.put({ url: `${project(id)}/${data.id}`, data })
export const restoreProjectContact = (projectId: number, id: number, version: number, expectedProjectVersion: number, status: number) => request.post({ url: `${project(projectId)}/${id}/actions/restore`, data: { expectedProjectVersion, status }, headers: { 'If-Match': String(version) } })
export const deleteProjectContact = (id: number, data: ContactVO, expectedProjectVersion: number, confirmNoPrimary: boolean) => request.delete({ url: `${project(id)}/${data.id}`, params: { expectedProjectVersion, confirmNoPrimary }, headers: { 'If-Match': String(data.version) } })
