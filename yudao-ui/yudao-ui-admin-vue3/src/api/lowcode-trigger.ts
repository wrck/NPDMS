import { del, get, post } from '@/utils/request'

/** 触发器类型：CRUD 数据增删改 / QUARTZ 定时 / EVENT 事件总线 */
export type TriggerType = 'CRUD' | 'QUARTZ' | 'EVENT'

/** 触发目标类型：MICROFLOW 微流 / PROCESS 流程 */
export type TriggerTargetType = 'MICROFLOW' | 'PROCESS'

export interface LowCodeTrigger {
  id?: number
  code: string
  name: string
  type: TriggerType
  /** 配置 JSON 字符串（按 type 解析为对应的 LowCodeTriggerConfig） */
  config: string
  targetType: TriggerTargetType
  targetCode: string
  status?: string
}

// ===================== 按 type 分发的结构化配置 =====================

/** CRUD 操作类型 */
export type CrudOperation = 'CREATE' | 'UPDATE' | 'DELETE'
/** CRUD 触发时机 */
export type CrudTiming = 'BEFORE' | 'AFTER'

/** CRUD 触发器配置（后端 CrudTriggerExecutor 约定） */
export interface CrudTriggerConfig {
  entityCode: string
  operations: CrudOperation[]
  timing: CrudTiming[]
  /**
   * 触发前置过滤条件（Groovy 表达式，可选）。
   *
   * <p>注：后端 CrudTriggerExecutor.matches 目前不支持 condition，
   * 本轮前端先存入 config.condition 字段，后端可选实现。</p>
   */
  condition?: string
}

/** Quartz 触发器配置（cronExpression 与 cron 两种字段名兼容） */
export interface QuartzTriggerConfig {
  /** Quartz cron 表达式（6 字段：秒 分 时 日 月 周，如 "0 0/5 * * * ?"） */
  cronExpression: string
}

/** 事件 payload 字段定义 */
export interface EventPayloadField {
  name: string
  type: string
}

/** 事件触发器配置（EventBus 事件名） */
export interface EventTriggerConfig {
  eventName: string
  /** 事件 payload schema（可选，字段名 + 类型） */
  payloadSchema?: EventPayloadField[]
}

/** 触发器配置联合类型（按 type 分发） */
export type LowCodeTriggerConfig = CrudTriggerConfig | QuartzTriggerConfig | EventTriggerConfig

// ===================== 配置 <-> JSON 字符串 转换 =====================

/** 安全解析 JSON 字符串 */
function safeParse(json: string): unknown {
  if (!json) return null
  try {
    return JSON.parse(json)
  } catch {
    return null
  }
}

/** 深拷贝纯数据对象（配置均为可 JSON 序列化的数据） */
function deepClone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T
}

/** 创建默认配置（按触发器类型） */
export function createDefaultTriggerConfig(type: TriggerType): LowCodeTriggerConfig {
  if (type === 'QUARTZ') {
    return { cronExpression: '0 0 * * * ?' }
  }
  if (type === 'EVENT') {
    return { eventName: '', payloadSchema: [] }
  }
  return { entityCode: '', operations: [], timing: [] }
}

/**
 * 将 config JSON 字符串解析为结构化配置对象，并兼容历史写法。
 *
 * <p>兼容场景：
 * <ul>
 *   <li>QUARTZ：{@code {cronExpression:"..."}} 或 {@code {cron:"..."}}</li>
 *   <li>CRUD：旧版单数 operation/timing 字段自动转为数组</li>
 *   <li>解析失败或为空时返回该类型的默认配置</li>
 * </ul></p>
 */
export function parseTriggerConfig(
  configStr: string | undefined,
  type: TriggerType
): LowCodeTriggerConfig {
  const parsed = safeParse(configStr || '')
  if (!parsed || typeof parsed !== 'object') {
    return createDefaultTriggerConfig(type)
  }
  const obj = parsed as Record<string, unknown>

  if (type === 'QUARTZ') {
    const cronExpression =
      (typeof obj.cronExpression === 'string' && obj.cronExpression) ||
      (typeof obj.cron === 'string' && obj.cron) ||
      '0 0 * * * ?'
    return { cronExpression }
  }

  if (type === 'EVENT') {
    const eventName = typeof obj.eventName === 'string' ? obj.eventName : ''
    const payloadSchema = Array.isArray(obj.payloadSchema)
      ? (obj.payloadSchema as EventPayloadField[]).map((f) => ({
          name: typeof f?.name === 'string' ? f.name : '',
          type: typeof f?.type === 'string' ? f.type : 'STRING'
        }))
      : []
    return { eventName, payloadSchema }
  }

  // CRUD
  const entityCode = typeof obj.entityCode === 'string' ? obj.entityCode : ''
  const operations = Array.isArray(obj.operations)
    ? (obj.operations as CrudOperation[]).filter((o) =>
        ['CREATE', 'UPDATE', 'DELETE'].includes(o)
      )
    : typeof obj.operation === 'string'
      ? ([obj.operation] as CrudOperation[])
      : []
  const timing = Array.isArray(obj.timing)
    ? (obj.timing as CrudTiming[]).filter((t) => ['BEFORE', 'AFTER'].includes(t))
    : typeof obj.timing === 'string'
      ? ([obj.timing] as CrudTiming[])
      : []
  const condition = typeof obj.condition === 'string' ? obj.condition : ''
  return { entityCode, operations, timing, condition }
}

/** 序列化结构化配置为 JSON 字符串（存入 LowCodeTrigger.config） */
export function serializeTriggerConfig(cfg: LowCodeTriggerConfig): string {
  return JSON.stringify(deepClone(cfg), null, 2)
}

// ===================== API 方法 =====================

export function getTriggerList() {
  return get<LowCodeTrigger[]>('/api/lowcode/trigger')
}

export function getTrigger(id: number) {
  return get<LowCodeTrigger>(`/api/lowcode/trigger/${id}`)
}

export function saveTrigger(data: LowCodeTrigger) {
  return post<LowCodeTrigger>('/api/lowcode/trigger', data)
}

export function deleteTrigger(id: number) {
  return del(`/api/lowcode/trigger/${id}`)
}

export function executeTrigger(code: string, data: Record<string, unknown>) {
  return post(`/api/lowcode/trigger/${code}/execute`, data)
}

// ===================== 触发器执行历史 =====================

/** 触发器执行状态 */
export type TriggerExecutionStatus = 'SUCCESS' | 'FAILED'

/** 触发器执行日志（与后端 LowCodeTriggerExecutionLog 对应） */
export interface LowCodeTriggerExecutionLog {
  id?: number
  triggerId: number
  triggerCode: string
  triggerType: TriggerType
  targetType: TriggerTargetType
  targetCode: string
  /** 执行唯一ID（微流执行ID，用于串联节点轨迹） */
  executionId?: string
  /** 输入数据 JSON 字符串 */
  inputs?: string
  /** 输出结果 JSON 字符串 */
  outputs?: string
  status: TriggerExecutionStatus
  errorMessage?: string
  /** 执行耗时毫秒 */
  durationMs?: number
  operator?: string
  createTime?: string
}

/** 查询指定触发器的执行历史（按时间倒序） */
export function getTriggerExecutionLogs(id: number, limit = 50) {
  return get<LowCodeTriggerExecutionLog[]>(`/api/lowcode/trigger/${id}/execution-logs`, { limit })
}

/** 查询全局最近触发器执行历史（按时间倒序） */
export function getRecentTriggerExecutionLogs(limit = 50) {
  return get<LowCodeTriggerExecutionLog[]>(`/api/lowcode/trigger/execution-logs/recent`, { limit })
}
