import request from '@/config/axios'

export interface EquipmentVO {
  id?: number
  serialNumber: string
  name: string
  model?: string
  customerId?: number
  customerName?: string
  projectId?: number
  projectName?: string
  status?: number
  location?: string
  warrantyStartDate?: Date
  warrantyEndDate?: Date
  remark?: string
  version?: number
  createTime?: Date
}

export interface EquipmentStatusChangeReqVO {
  id: number
  action: string
  targetStatus?: number
  changeDescription?: string
}

export interface EquipmentVersionVO {
  id?: number
  equipmentId?: number
  versionNo?: number
  changeType?: string
  changeDescription?: string
  beforeSnapshot?: string
  afterSnapshot?: string
  creator?: string
  createTime?: Date
}

export interface EquipmentConfigLogVO {
  id?: number
  equipmentId?: number
  configType?: string
  configContent?: string
  sourceSystem?: string
  collectedAt?: Date
  fileUrl?: string
  fileHash?: string
  remark?: string
  createTime?: Date
}

const baseUrl = '/pms/equipment'

export const getEquipmentPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
export const getEquipment = (id: number) =>
  request.get({ url: `${baseUrl}/get`, params: { id } })
export const createEquipment = (data: EquipmentVO) =>
  request.post({ url: `${baseUrl}/create`, data })
export const updateEquipment = (data: EquipmentVO) =>
  request.put({ url: `${baseUrl}/update`, data })
export const deleteEquipment = (id: number) =>
  request.delete({ url: `${baseUrl}/delete`, params: { id } })
export const changeEquipmentStatus = (data: EquipmentStatusChangeReqVO) =>
  request.put({ url: `${baseUrl}/status-change`, data })
export const getEquipmentVersionList = (equipmentId: number) =>
  request.get({ url: `${baseUrl}/version/list`, params: { equipmentId } })
export const getEquipmentConfigLogPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/config-log/page`, params })
