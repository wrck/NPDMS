import request from '@/config/axios'

export interface UserOrganizationScope {
  id: number
  userId: number
  companyId: number
  companyCode: string
  companyName: string
  departmentId?: number
  departmentCode?: string
  departmentName?: string
  scopeRole: string
  isPrimary: boolean
  effectiveFrom: string | number
  effectiveTo?: string | number
  status: number
  version: number
}

export interface UserOrganizationScopeSave {
  userId: number
  companyId: number
  departmentId?: number
  scopeRole: string
  isPrimary: boolean
  effectiveFrom: string | number
  effectiveTo?: string | number
  expectedVersion?: number
}

const baseUrl = '/api/v1/pms/user-organization-scopes'

export const getUserOrganizationScopes = (
  params: PageParam & { userId: number; status?: number }
): Promise<{ list: UserOrganizationScope[]; total: number }> =>
  request.get({ url: baseUrl, params })

export const createUserOrganizationScope = (data: UserOrganizationScopeSave): Promise<number> =>
  request.post({ url: baseUrl, data })

export const reviseUserOrganizationScope = (
  id: number,
  data: UserOrganizationScopeSave
): Promise<number> => request.post({ url: `${baseUrl}/${id}/actions/revise`, data })

export const disableUserOrganizationScope = (data: {
  id: number
  userId: number
  expectedVersion: number
}) =>
  request.post({
    url: `${baseUrl}/${data.id}/actions/disable`,
    data: { userId: data.userId, expectedVersion: data.expectedVersion }
  })
