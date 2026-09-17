import { getAccessToken, getTenantId, getVisitTenantId } from '@/utils/auth'

/** Same-origin migrated lowcode APIs use the current NPDMS login and tenant. */
export function lowcodeSessionHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}
  const token = getAccessToken() || localStorage.getItem('pms_token')
  if (token) headers.Authorization = `Bearer ${token}`
  if ((import.meta.env.VITE_APP_TENANT_ENABLE ?? 'true') === 'true') {
    const tenantId = getTenantId()
    const visitTenantId = getVisitTenantId()
    if (tenantId != null) headers['tenant-id'] = String(tenantId)
    if (token && visitTenantId != null) headers['visit-tenant-id'] = String(visitTenantId)
  }
  return headers
}
