/** Optional version-local operation rules. These types do not grant execution permission. */
export type OperationCheck = { mode: 'NONE' } | { mode: 'RULE'; ruleKey: string }

export interface TemplateOperation {
  operationCode: string
  operationVersion: number
  pre: OperationCheck
  post: OperationCheck
}

export interface TemplateOperationContract {
  version: 1
  operations: TemplateOperation[]
}
