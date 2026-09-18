/** 配置只选择业务动作和结果，不携带业务DTO技术版本或调用类名。 */
export type ExecutionCheck = { mode: 'NONE' } | { mode: 'RULE'; ruleKey: string }
export interface ExecutionOperation {
  ownerContext: string
  entityType: string
  permissionCode: string
  operationCode?: string
  pre: ExecutionCheck
  post: ExecutionCheck
}
export type ExecutionScope = { mode: 'PROJECT' } | { mode: 'OBJECTS'; objectIds: string[] }
export interface ExecutionEvidencePolicy {
  acquisition: 'REUSE_EXISTING' | 'NEW_RESULT' | 'PINNED_RESULT'
  validity: 'HISTORICAL_FACT' | 'CURRENT_VALID'
  selection: 'EXACT_ONE' | 'ANY_MATCHING' | 'ALL_EXPECTED'
  pinnedResultId?: string
}
export interface ExecutionSubscription {
  key: string
  ownerContext: string
  entityType: string
  resultType: string
  scope: ExecutionScope
  policy: ExecutionEvidencePolicy
}
export interface NodeExecutionConfiguration {
  operations?: ExecutionOperation[]
  subscriptions?: ExecutionSubscription[]
  presentation?: { pageUrl: string; query?: Record<string, string> }
}
