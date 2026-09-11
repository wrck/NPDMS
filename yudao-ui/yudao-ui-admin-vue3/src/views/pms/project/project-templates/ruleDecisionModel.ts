import type { JsonObject } from '@/api/pms/project/project-templates/definitions'

export interface DecisionRow {
  conditions: JsonObject[]
}

export const cloneRule = <T>(value: T): T => JSON.parse(JSON.stringify(value))
export const blankPredicate = (): JsonObject => ({ predicate: '', parameters: { refCode: '' } })
export const isPredicateRule = (rule: JsonObject) =>
  !!rule && !rule.operator && Object.prototype.hasOwnProperty.call(rule, 'predicate')

/**
 * Decision-table normal form:
 * - each row is ALL(conditions)
 * - rows are combined with ANY
 * A single row / condition is collapsed back to the smallest equivalent rule shape.
 * Nested structures outside that normal form remain tree-only and are never rewritten.
 */
export const decodeDecisionRows = (rule: JsonObject): DecisionRow[] | undefined => {
  if (isPredicateRule(rule)) return [{ conditions: [cloneRule(rule)] }]
  if (rule?.operator === 'ALL' && Array.isArray(rule.rules) && rule.rules.every(isPredicateRule)) {
    return [{ conditions: cloneRule(rule.rules) }]
  }
  if (rule?.operator === 'ANY' && Array.isArray(rule.rules)) {
    const rows: DecisionRow[] = []
    for (const child of rule.rules) {
      if (isPredicateRule(child)) rows.push({ conditions: [cloneRule(child)] })
      else if (child?.operator === 'ALL' && Array.isArray(child.rules) && child.rules.every(isPredicateRule)) {
        rows.push({ conditions: cloneRule(child.rules) })
      } else return undefined
    }
    return rows
  }
  return undefined
}

export const encodeDecisionRows = (rows: DecisionRow[]): JsonObject => {
  if (!rows.length) return blankPredicate()
  const groups = rows.map((row) =>
    row.conditions.length === 1
      ? cloneRule(row.conditions[0])
      : { operator: 'ALL', rules: cloneRule(row.conditions) }
  )
  return groups.length === 1 ? groups[0] : { operator: 'ANY', rules: groups }
}
