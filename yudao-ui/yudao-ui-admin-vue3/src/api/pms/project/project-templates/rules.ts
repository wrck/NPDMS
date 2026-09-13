import request from '@/config/axios'
import type { JsonObject, JsonValue } from './index'

export type RuleValueType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATETIME'
export interface RuleField {
  code: string
  label: string
  valueType: RuleValueType
  availableAtCreation: boolean
}
export interface DecisionTableDefinition {
  key: string
  name: string
  decisionKey: string
  xml: string
  inputFields: Record<string, string>
}
export interface VersionRule {
  key: string
  name: string
  kind: 'CONDITION' | 'DECISION'
  shared: boolean
  expression?: JsonObject
  decision?: DecisionTableDefinition
}
export interface RuleConditionDiagnostic {
  key: string
  path: string
  component: string
  outcome: 'MATCHED' | 'NOT_MATCHED' | 'UNKNOWN'
  reasonCode?: string
}
interface ResultContext {
  ruleVersionRef: string
  reasonCode?: string
  conditions: RuleConditionDiagnostic[]
  steps: string[]
  diagnostics: { path: string; component: string; code: string; inputKey?: string }[]
}
export type RuleResult = ResultContext &
  (
    | { kind: 'CONDITION'; outcome: 'MATCHED' | 'NOT_MATCHED' | 'UNKNOWN' }
    | { kind: 'DECISION'; status: 'AVAILABLE' | 'UNKNOWN'; values: Record<string, JsonValue>[] }
  )
export interface RuleSimulation {
  el: string
  inputs: { key: string; label: string; valueType: RuleValueType }[]
  evaluation: RuleResult
  decisions: Record<
    string,
    { available: boolean; reasonCode?: string; rows: Record<string, JsonValue>[] }
  >
}
export const getRuleFields = (): Promise<RuleField[]> =>
  request.get({ url: '/api/v1/pms/project-templates/rules/fields' })
export const simulateRule = (
  rules: VersionRule[],
  ruleKey: string,
  facts: Record<string, JsonValue>
): Promise<RuleSimulation> =>
  request.post({
    url: '/api/v1/pms/project-templates/rules/simulate',
    data: { rules, ruleKey, facts }
  })
