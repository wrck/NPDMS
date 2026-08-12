import request from '@/config/axios'

export interface MaintenanceTransitionVO {
  id?: number
  projectId: number
  equipmentId?: number
  code: string
  name: string
  acceptanceDate?: Date
  warrantyStartDate?: Date
  warrantyEndDate?: Date
  servicePlanUrl?: string
  status?: number
  remark?: string
  version?: number
  createTime?: Date
}

const baseUrl = '/pms/acc-maintenance-transition'

export const getMaintenanceTransitionPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getMaintenanceTransition = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createMaintenanceTransition = (data: MaintenanceTransitionVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateMaintenanceTransition = (data: MaintenanceTransitionVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteMaintenanceTransition = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
// 状态动作: 0草稿 1待生效 2生效中 3已过期 4已续保
export const submitMaintenanceTransition = (id: number) =>
  request.put({ url: `${baseUrl}/submit`, params: { id } })
export const activateMaintenanceTransition = (id: number) =>
  request.put({ url: `${baseUrl}/activate`, params: { id } })
export const expireMaintenanceTransition = (id: number) =>
  request.put({ url: `${baseUrl}/expire`, params: { id } })
export const renewMaintenanceTransition = (id: number) =>
  request.put({ url: `${baseUrl}/renew`, params: { id } })
