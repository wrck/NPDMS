import request from '@/config/axios'
import { businessViewIdKey, type BusinessViewId } from './ids'
export type { BusinessViewId } from './ids'

// PM-03 / F-PLT-003 — registration is configuration, never object authorization.
export type BusinessViewSource = 'PAGE' | 'DYNAMIC_FORM'
export type BusinessViewAction = 'UPDATE' | 'COPY' | 'VALIDATE' | 'PUBLISH' | 'DISABLE'
export interface BusinessViewComponentVO {
  entityType: string
  ownerContext: string
  viewSource: BusinessViewSource
  componentKey: string
  componentVersion: string
  contextSchema: Record<string, unknown>
  supportedActions: string[]
  queryProviderKey: string
  commandProviderKey: string
  permissionProviderKey: string
  /** Owner声明的组件展示名；目录元数据不可回传服务端。 */
  displayName?: string
}
export interface BusinessViewRegistrationVO extends BusinessViewComponentVO {
  id: BusinessViewId
  viewKey: string
  revisionNo: number
  dynamicFormRevisionId?: BusinessViewId
  publishedAt?: string
  disabledAt?: string
  version: number
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED'
  allowedActions: string[]
}
export interface BusinessViewSelection {
  componentKey: string
  componentVersion: string
  dynamicFormRevisionId?: BusinessViewId
}
export interface BusinessViewCreate extends BusinessViewSelection {
  entityType: string
  viewKey: string
}
export interface BusinessViewPageQuery {
  pageNo: number
  pageSize: number
  entityType?: string
  viewSource?: BusinessViewSource
}
export interface BusinessViewValidation {
  valid: boolean
  issues: { field: string; code: string; message: string }[]
}
const baseUrl = '/api/v1/pms/business-views'
const headers = (version: number, key: string) => ({
  'If-Match': String(version),
  'Idempotency-Key': key
})
// Pick the public Body fields explicitly; directory metadata must never be posted back.
const selectionBody = (data: BusinessViewSelection): BusinessViewSelection => ({
  componentKey: data.componentKey,
  componentVersion: data.componentVersion,
  ...(data.dynamicFormRevisionId ? { dynamicFormRevisionId: data.dynamicFormRevisionId } : {})
})
export const getBusinessViewPage = (params: BusinessViewPageQuery) =>
  request.get<{ list: BusinessViewRegistrationVO[]; total: number }>({ url: baseUrl, params })
export const getBusinessViewComponents = () =>
  request.get<BusinessViewComponentVO[]>({ url: `${baseUrl}/components` })
export const getBusinessView = (id: BusinessViewId) =>
  request.get<BusinessViewRegistrationVO>({ url: `${baseUrl}/${businessViewIdKey(id)}` })
export const createBusinessView = (data: BusinessViewCreate, key: string) =>
  request.post<BusinessViewRegistrationVO>({
    url: baseUrl,
    data: { entityType: data.entityType, viewKey: data.viewKey, ...selectionBody(data) },
    headers: { 'Idempotency-Key': key }
  })
export const updateBusinessView = (
  id: BusinessViewId,
  version: number,
  data: BusinessViewCreate,
  key: string
) =>
  request.put<BusinessViewRegistrationVO>({
    url: `${baseUrl}/${businessViewIdKey(id)}`,
    data: { entityType: data.entityType, viewKey: data.viewKey, ...selectionBody(data) },
    headers: headers(version, key)
  })
const command = (id: BusinessViewId, action: string, version: number, key: string) =>
  request.post<BusinessViewRegistrationVO>({
    url: `${baseUrl}/${businessViewIdKey(id)}/actions/${action}`,
    headers: headers(version, key)
  })
export const copyBusinessView = (id: BusinessViewId, version: number, key: string) =>
  command(id, 'copy', version, key)
export const publishBusinessView = (id: BusinessViewId, version: number, key: string) =>
  command(id, 'publish', version, key)
export const disableBusinessView = (id: BusinessViewId, version: number, key: string) =>
  command(id, 'disable', version, key)
export const validateBusinessView = (id: BusinessViewId) =>
  request.post<BusinessViewValidation>({
    url: `${baseUrl}/${businessViewIdKey(id)}/actions/validate`
  })

export const isBusinessViewConflict = (error: any) => {
  const status = error?.response?.status ?? error?.status
  const code = String(error?.response?.data?.code ?? error?.data?.code ?? error?.code ?? '')
  return (
    status === 409 ||
    status === 412 ||
    code === '1010004002' ||
    /VERSION_CONFLICT|OPTIMISTIC_LOCK|STALE_VERSION/.test(code)
  )
}
