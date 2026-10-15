import request from '@/config/axios'
import type { WireLong } from '@/api/pms/inspection/tasks'

export interface CustomerInspectionHistory {
  id: WireLong
  projectId: WireLong
  projectCode?: string
  projectName: string
  taskId: WireLong
  taskVersion: WireLong
  taskCode: string
  taskName: string
  reportId: WireLong
  archivedBy: WireLong
  archivedAt: number
  references: { kind: string; sourceId: WireLong }[]
}
export const getCustomerInspectionHistory = (customerId: WireLong, params: { pageNo: number; pageSize: number }) =>
  request.get<{ list: CustomerInspectionHistory[]; total: number }>({
    url: `/pms/customers/${customerId}/inspection-history`, params
  })
