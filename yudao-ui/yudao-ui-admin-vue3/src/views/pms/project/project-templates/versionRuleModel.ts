import type { JsonObject, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import type { VersionRule } from '@/api/pms/project/project-templates/rules'
import type { OperationWorkBindingSpec } from '@/api/pms/project/project-templates/operations'
import type { ComputedRef, InjectionKey } from 'vue'

export const ruleCreationOnlyKey: InjectionKey<ComputedRef<boolean>> = Symbol('rule-creation-only')

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))
export const constantRule = (value: boolean): JsonObject => ({
  predicate: 'CONSTANT',
  parameters: { value }
})

export function createVersionRule(
  document: TemplateDesignerDocument,
  name: string,
  expression: JsonObject
): VersionRule {
  const rule: VersionRule = {
    key: `rule_${crypto.randomUUID()}`,
    name,
    kind: 'CONDITION',
    shared: false,
    expression: clone(expression)
  }
  ;(document.rules ??= []).push(rule)
  return rule
}

export function copyVersionRule(
  document: TemplateDesignerDocument,
  sourceKey: string
): VersionRule {
  const copies = new Map<string, VersionRule>()
  const copy = (key: string): VersionRule => {
    const existing = copies.get(key)
    if (existing) return existing
    const source = document.rules?.find((rule) => rule.key === key)
    if (!source) throw new Error(`本版本不存在规则：${key}`)
    const result = clone(source)
    result.key = `rule_${crypto.randomUUID()}`
    result.name = `${source.name}（独立）`
    result.shared = false
    copies.set(key, result)
    if (result.expression)
      visitDecisionReferences(result.expression, (reference) => {
        reference.ruleKey = copy(String(reference.ruleKey)).key
      })
    return result
  }
  const result = copy(sourceKey)
  ;(document.rules ??= []).push(...copies.values())
  return result
}

function visitDecisionReferences(expression: JsonObject, visit: (parameters: JsonObject) => void) {
  if (expression.operator && Array.isArray(expression.rules)) {
    for (const child of expression.rules)
      if (child && typeof child === 'object' && !Array.isArray(child))
        visitDecisionReferences(child, visit)
  } else if (expression.predicate === 'DECISION') {
    const parameters = expression.parameters
    if (
      parameters &&
      typeof parameters === 'object' &&
      !Array.isArray(parameters) &&
      parameters.ruleKey
    )
      visit(parameters)
  }
}

export function ruleUsedForMatching(document: TemplateDesignerDocument, key?: string): boolean {
  if (!key || !document.matchRuleKey) return false
  if (document.matchRuleKey === key) return true
  const expression = document.rules?.find(rule => rule.key === document.matchRuleKey)?.expression
  let used = false
  if (expression) visitDecisionReferences(expression, parameters => { if (parameters.ruleKey === key) used = true })
  return used
}

export function ruleUses(document: TemplateDesignerDocument, key: string): string[] {
  const users: string[] = []
  const reference = (selected: string | undefined, label: string) => {
    if (!selected) return
    let used = selected === key
    const rule = document.rules?.find((item) => item.key === selected)
    if (rule?.expression)
      visitDecisionReferences(rule.expression, (parameters) => {
        if (parameters.ruleKey === key) used = true
      })
    // Deduplicate repeated decision references within one slot, never distinct nodes with the same name.
    if (used) users.push(label)
  }
  reference(document.matchRuleKey, '模板适用条件')
  reference(document.closureRuleKey, '项目收口')
  for (const node of [...document.stages, ...document.tasks]) {
    const label = `${'stageCode' in node ? '任务' : '阶段'} ${node.name}（${node.code}）`
    reference(node.admissionRuleKey, `${label} · 准入`)
    reference(node.completionRuleKey, `${label} · 完成`)
    reference(node.exitRuleKey, `${label} · 退出`)
    const contract = (node.workBinding as OperationWorkBindingSpec | undefined)?.operationContract
    if (Array.isArray(contract?.operations)) for (const operation of contract.operations) {
      if (operation?.pre?.mode === 'RULE') reference(operation.pre.ruleKey, `${label} · ${operation.operationCode} · 前置`)
      if (operation?.post?.mode === 'RULE') reference(operation.post.ruleKey, `${label} · ${operation.operationCode} · 后置`)
    }
  }
  for (const edge of document.transitions)
    reference(edge.conditionRuleKey, `${edge.code} · 依赖条件`)
  return users
}
