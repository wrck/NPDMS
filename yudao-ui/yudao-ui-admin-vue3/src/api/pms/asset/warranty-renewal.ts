import request from '@/config/axios'

export interface WarrantyRenewalRecord {
  id: number
  deviceSn?: string
  warrantyContractNo?: string
  warrantyGrade?: string
  warrantyStartDate?: string
  warrantyEndDate?: string
  warrantyMonths?: number
  extended?: boolean | null
  isLatest?: boolean | null
  remark?: string
}

export const getWarrantyRenewals = (params: {
  projectId?: number
  deviceId?: number
  sn?: string
  pageNo: number
  pageSize: number
}) =>
  request.get<{ list: WarrantyRenewalRecord[]; total: number }>({
    url: '/api/v1/pms/asset/warranty-renewals/page',
    params
  })
