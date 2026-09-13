import type { JsonObject, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import type { VersionRule } from '@/api/pms/project/project-templates/rules'

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

export function ruleUses(document: TemplateDesignerDocument, key: string): string[] {
  const users = new Set<string>()
  const reference = (selected: string | undefined, label: string) => {
    if (!selected) return
    if (selected === key) users.add(label)
    const rule = document.rules?.find((item) => item.key === selected)
    if (rule?.expression)
      visitDecisionReferences(rule.expression, (parameters) => {
        if (parameters.ruleKey === key) users.add(label)
      })
  }
  reference(document.matchRuleKey, '模板适用条件')
  reference(document.closureRuleKey, '项目收口')
  for (const node of [...document.stages, ...document.tasks]) {
    reference(node.admissionRuleKey, `${node.name} · 准入`)
    reference(node.completionRuleKey, `${node.name} · 完成`)
    reference(node.exitRuleKey, `${node.name} · 退出`)
  }
  for (const edge of document.transitions)
    reference(edge.conditionRuleKey, `${edge.code} · 依赖条件`)
  return [...users]
}
