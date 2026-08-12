import request from '@/config/axios'

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

// 后端 EquipmentController 中 config-log 分页接口的实际路径为
// `/pms/equipment/config-log/page`，因此 baseUrl 复用 `/pms/equipment/config-log`。
const baseUrl = '/pms/equipment/config-log'

export const getEquipmentConfigLogPage = (params: PageParam) =>
  request.get({ url: `${baseUrl}/page`, params })
