import { del, get, post } from '@/utils/request'
import type { LowCodeConfigVersion } from '@/api/lowcode-version'

export interface LowCodeRule {
  id?: number
  code: string
  name: string
  description?: string
  type: 'DECISION_TABLE' | 'EXPRESSION' | 'LITEFLOW'
  definition: string
  status?: string
  version?: number
  bizType?: string
  /** 扩展信息 JSON 字符串（如表达式规则的 inputsSchema） */
  ext?: string
}

// ===================== 决策表结构化类型 =====================

/** 比较操作符 */
export type DecisionOperator = 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'IN'

/** 命中策略：FIRST 匹配首行 / ALL 匹配全部 / COLLECT 收集全部 */
export type HitPolicy = 'FIRST' | 'ALL' | 'COLLECT'

/** 条件列定义：字段名 + 操作符 */
export interface ConditionColumn {
  field: string
  operator: DecisionOperator
}

/** 动作列定义：字段名 */
export interface ActionColumn {
  field: string
}

/** 决策表行：按列顺序对应条件值与动作值 */
export interface DecisionRow {
  conditions: { value: unknown }[]
  actions: { value: unknown }[]
}

/** 决策表完整结构（新格式 definition） */
export interface DecisionTable {
  hitPolicy: HitPolicy
  conditionColumns: ConditionColumn[]
  actionColumns: ActionColumn[]
  rows: DecisionRow[]
}

/** 表达式规则扩展信息（存入 ext 字段） */
export interface ExpressionExt {
  /** 用户手动维护的 facts schema，用于变量侧栏补全 */
  inputsSchema: { name: string; type?: string; description?: string }[]
}

export function getRuleList() {
  return get<LowCodeRule[]>('/api/lowcode/rule')
}

export function getRule(id: number) {
  return get<LowCodeRule>(`/api/lowcode/rule/${id}`)
}

export function saveRule(data: LowCodeRule) {
  return post<LowCodeRule>('/api/lowcode/rule', data)
}

export function deleteRule(id: number) {
  return del(`/api/lowcode/rule/${id}`)
}

export function executeRule(code: string, facts: Record<string, unknown>) {
  return post(`/api/lowcode/rule/${code}/execute`, facts)
}

// ===================== 规则版本管理 =====================

/** 发布规则并生成版本快照 */
export function publishRuleWithVersion(id: number) {
  return post<LowCodeConfigVersion>(`/api/lowcode/rule/${id}/publish`)
}

/** 查询规则版本历史 */
export function getRuleVersions(id: number) {
  return get<LowCodeConfigVersion[]>(`/api/lowcode/rule/${id}/versions`)
}

/** 回滚规则到指定版本 */
export function rollbackRule(id: number, targetVersion: number) {
  return post<void>(`/api/lowcode/rule/${id}/rollback/${targetVersion}`)
}
