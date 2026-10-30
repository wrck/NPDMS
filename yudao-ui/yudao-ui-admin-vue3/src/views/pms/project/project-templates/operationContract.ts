/** Authoring-only validation. The backend validates deployment, rules and authority. */
import type { OperationCheck, TemplateOperation, TemplateOperationContract } from '../../../../api/pms/project/project-templates/operationTypes'
export type { OperationCheck, TemplateOperation, TemplateOperationContract } from '../../../../api/pms/project/project-templates/operationTypes'

export class OperationContractAuthoringError extends Error {
  readonly path: string
  readonly code: string

  constructor(path: string, code: string, message: string) {
    super(message)
    this.name = 'OperationContractAuthoringError'
    this.path = path
    this.code = code
  }
}

function fail(path: string, code: string, message: string): never {
  throw new OperationContractAuthoringError(path, code, message)
}

function record(value: unknown, path: string): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value))
    return fail(path, 'OBJECT_REQUIRED', '必须提供明确的对象')
  const prototype = Object.getPrototypeOf(value)
  if (prototype !== Object.prototype && prototype !== null)
    return fail(path, 'PLAIN_OBJECT_REQUIRED', '配置必须是 JSON 对象')
  return value as Record<string, unknown>
}

function keys(value: Record<string, unknown>, allowed: readonly string[], path: string): void {
  const extra = Object.keys(value).find((key) => !allowed.includes(key))
  if (extra !== undefined) fail(`${path}.${extra}`, 'UNKNOWN_FIELD', '存在不支持的配置字段')
}

function text(value: unknown, path: string): string {
  if (typeof value !== 'string' || !value.trim())
    return fail(path, 'NONEMPTY_STRING_REQUIRED', '必须提供非空字符串')
  // Do not trim identifiers and accidentally reference a different frozen rule.
  return value
}

function version(value: unknown, path: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1)
    return fail(path, 'EXACT_VERSION_REQUIRED', '必须提供精确的正整数版本')
  return value
}

function check(value: unknown, path: string): OperationCheck {
  const input = record(value, path)
  if (input.mode === 'NONE') {
    keys(input, ['mode'], path)
    return { mode: 'NONE' }
  }
  if (input.mode === 'RULE') {
    keys(input, ['mode', 'ruleKey'], path)
    return { mode: 'RULE', ruleKey: text(input.ruleKey, `${path}.ruleKey`) }
  }
  return fail(`${path}.mode`, 'CHECK_MODE_INVALID', '必须明确选择 NONE 或 RULE')
}

/** Absent means legacy. Explicit null, malformed input and unknown versions are errors. */
export function readOperationContract(value: unknown): TemplateOperationContract | undefined {
  if (value === undefined) return undefined
  const path = 'operationContract'
  const input = record(value, path)
  keys(input, ['version', 'operations'], path)
  if (input.version !== 1)
    return fail(`${path}.version`, 'CONTRACT_VERSION_UNSUPPORTED', '不支持此操作子契约版本')
  if (!Array.isArray(input.operations) || input.operations.length === 0)
    return fail(`${path}.operations`, 'OPERATIONS_REQUIRED', '必须明确声明操作')
  const seen = new Set<string>()
  const operations = input.operations.map((value, index): TemplateOperation => {
    const operationPath = `${path}.operations[${index}]`
    const item = record(value, operationPath)
    keys(item, ['operationCode', 'operationVersion', 'pre', 'post'], operationPath)
    const operationCode = text(item.operationCode, `${operationPath}.operationCode`)
    if (seen.has(operationCode))
      return fail(`${operationPath}.operationCode`, 'OPERATION_DUPLICATE', '操作不能重复')
    seen.add(operationCode)
    return {
      operationCode,
      operationVersion: version(item.operationVersion, `${operationPath}.operationVersion`),
      pre: check(item.pre, `${operationPath}.pre`),
      post: check(item.post, `${operationPath}.post`)
    }
  })
  operations.sort((left, right) =>
    left.operationCode < right.operationCode ? -1 : left.operationCode > right.operationCode ? 1 : 0
  )
  return { version: 1, operations }
}

/** Preserve all unrelated binding fields and never append an empty legacy contract. */
export function withOperationContract<T extends object>(
  binding: T,
  value: unknown,
  readonly: boolean = false
): Omit<T, 'operationContract'> & { operationContract?: TemplateOperationContract } {
  if (readonly) return fail('operationContract', 'READ_ONLY', '只读配置不能修改')
  const next = { ...binding } as Omit<T, 'operationContract'> & {
    operationContract?: TemplateOperationContract
  }
  const contract = readOperationContract(value)
  if (contract === undefined) delete next.operationContract
  else next.operationContract = contract
  return next
}

/** Replaces one check without mutating the edited node, its other operations or rule definitions. */
export function replaceOperationCheck(
  value: TemplateOperationContract,
  operationCode: string,
  phase: 'pre' | 'post',
  replacement: unknown
): TemplateOperationContract {
  if (phase !== 'pre' && phase !== 'post')
    return fail('operationContract', 'CHECKPOINT_INVALID', '不支持此检查点')
  const contract = readOperationContract(value)
  if (contract === undefined) return fail('operationContract', 'CONTRACT_REQUIRED', '尚未启用操作子契约')
  const target = contract.operations.find((operation) => operation.operationCode === operationCode)
  if (!target) return fail('operationContract.operations', 'OPERATION_NOT_FOUND', '目标操作不存在')
  target[phase] = check(replacement, `operationContract.operations.${operationCode}.${phase}`)
  return contract
}
