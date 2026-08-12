import request from '@/config/axios'

export interface SrvMaintenanceVO {
  id?: number
  equipmentId: number
  projectId?: number
  code: string
  startDate?: Date
  endDate?: Date
  maintenanceStatus?: number
  serviceLevel?: string
  autoCalculated?: boolean
  manualOverride?: boolean
  overrideBy?: number
  overrideTime?: Date
  remark?: string
  version?: number
  createTime?: Date
}

export interface SrvMaintenanceOverrideVO {
  id: number
  maintenanceStatus: number
  version?: number
}

const baseUrl = '/pms/srv-maintenance'

export const getSrvMaintenancePage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getSrvMaintenance = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createSrvMaintenance = (data: SrvMaintenanceVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateSrvMaintenance = (data: SrvMaintenanceVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteSrvMaintenance = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const getSrvMaintenanceListByEquipment = (equipmentId: number) =>
  request.get({ url: `${baseUrl}/list-by-equipment`, params: { equipmentId } })
export const calculateStatus = (id: number) =>
  request.put({ url: `${baseUrl}/calculate`, params: { id } })
export const manualOverride = (data: SrvMaintenanceOverrideVO) =>
  request.put({ url: `${baseUrl}/override`, data })
