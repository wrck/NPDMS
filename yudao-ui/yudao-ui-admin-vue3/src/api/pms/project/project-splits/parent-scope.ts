import request from '@/config/axios'
import type { DeliveryScopeRespVO, DeliveryScopePageReqVO } from '@/api/pms/commerce'

// The split workspace uses the current COM department contract; keep the legacy page unchanged.
type DepartmentFact = { departmentId: number; departmentCode: string; departmentName: string; departmentVersion: number }
type LegacyDepartmentFields = 'officeDepartmentId' | 'officeDepartmentCode' | 'officeDepartmentName' | 'officeDepartmentVersion'
export type ParentScope = Omit<DeliveryScopeRespVO, LegacyDepartmentFields | 'effectiveFrom' | 'effectiveTo'> & DepartmentFact & {
  effectiveFrom: string | number
  effectiveTo?: string | number
}
export const getParentScopes = async (projectId: number, orderLineId?: number, includeHistory = false) => {
  const rows: ParentScope[] = []
  for (let pageNo = 1; ; pageNo++) {
    const params: DeliveryScopePageReqVO = { projectId, orderLineId, includeHistory, pageNo, pageSize: 200 }
    const page = await request.get<{ list: ParentScope[]; total: number }>({ url: '/api/v1/pms/delivery-scopes', params })
    rows.push(...page.list)
    if (rows.length >= page.total || !page.list.length) return rows
  }
}

export const scopeStatusLabel = (status: string) => ({ ACTIVE: '当前有效', RELEASED: '已释放', CONFLICT_FROZEN: '冲突冻结' })[status] || status
