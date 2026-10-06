import { getCurrentUserId, getTenantId, getVisitTenantId } from '@/utils/auth'

export interface PendingBusinessIntent {
  key: string
  fingerprint: string
  operation: string
  operationVersion: number
  entityId?: string
  concurrencyBasis?: number
  revisionId?: string
}

// Recovery locators contain no business values, passwords, tokens or private keys.
export const businessIntentStorageKey = (owner: string, type: string) => {
  const user = getCurrentUserId()
  const tenant = getVisitTenantId() ?? getTenantId()
  return user > 0 && tenant != null ? `pms-business-intent:v1:${tenant}:${user}:${owner}:${type}` : undefined
}
export const readBusinessIntent = (key: string | undefined): PendingBusinessIntent | undefined => {
  if (!key) return undefined
  const raw = sessionStorage.getItem(key)
  if (!raw) return undefined
  const intent: PendingBusinessIntent = JSON.parse(raw)
  if (typeof intent.key !== 'string' || !intent.key || !/^[0-9a-f]{64}$/.test(intent.fingerprint) ||
      typeof intent.operation !== 'string' || !intent.operation || !Number.isInteger(intent.operationVersion) || intent.operationVersion < 1) {
    throw new Error('待确认操作记录无效，无法安全重试')
  }
  return intent
}
export const writeBusinessIntent = (key: string, intent: PendingBusinessIntent) =>
  sessionStorage.setItem(key, JSON.stringify(intent))
export const clearBusinessIntent = (key: string, completed: PendingBusinessIntent) => {
  const current = readBusinessIntent(key)
  if (current?.key === completed.key && current.fingerprint === completed.fingerprint) sessionStorage.removeItem(key)
}
const canonical = (value: unknown): unknown => Array.isArray(value) ? value.map(canonical)
  : value && typeof value === 'object'
    ? Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => [key, canonical(item)]))
    : value
export const businessIntentFingerprint = async (value: unknown) => {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(canonical(value))))
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
}
