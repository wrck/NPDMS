import type { CapabilityQuery, OperationCapabilities } from '@/api/pms/project/execution-operations'
import { getCurrentUserId, getTenantId, getVisitTenantId } from '@/utils/auth'

export interface OperationCapabilityPrefetch {
  take: (query: CapabilityQuery) => Promise<OperationCapabilities> | undefined
}
const identity = (query: CapabilityQuery) => JSON.stringify([
  String(query.projectId), query.nodeKind, String(query.nodeId),
  query.objectId == null ? null : String(query.objectId), query.expected
])
const caller = () => JSON.stringify([getCurrentUserId(), getTenantId(), getVisitTenantId()])

/** The task opening already resolved this observation; this handoff issues no HTTP request. */
export function resolvedOperationCapabilities(query: CapabilityQuery, value: OperationCapabilities): OperationCapabilityPrefetch {
  const key = identity(query), actor = caller(), started = Date.now()
  let consumed = false
  return { take: expected => {
    if (consumed) return
    consumed = true
    return key === identity(expected) && actor === caller() && Date.now() - started < 4000 ? Promise.resolve(value) : undefined
  } }
}
