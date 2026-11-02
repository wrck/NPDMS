import request from '@/config/axios'
export interface ProjectShipment {
  id: number
  deviceSn?: string
  productCode?: string
  contractNo?: string
  orderNo?: string
  lineNo?: string
  shipmentTime?: string | number
  carrierName?: string
  expressNo?: string
  receiverName?: string
  warrantyStartDate?: string | number
  warrantyMonths?: number
}
export const getProjectShipments = (params: {
  projectId: number
  sn?: string
  pageNo: number
  pageSize: number
}) =>
  request.get<{ list: ProjectShipment[]; total: number }>({
    url: '/api/v1/pms/asset/project-shipments/page',
    params
  })
