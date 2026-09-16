import { del, get, post } from '@/utils/request'
import { triggerBlobDownload } from '@/api/excel'

export interface LowCodeMicroflow {
  id?: number
  code: string
  name: string
  description?: string
  definition?: string
  status?: string
  version?: number
  bizType?: string
}

/** 微流节点类型（与后端 MicroflowNodeType 枚举对齐） */
export type MicroflowNodeType =
  | 'START'
  | 'END'
  | 'ASSIGN'
  | 'CONDITION'
  | 'LOOP'
  | 'CALL_SERVICE'
  | 'CALL_MICROFLOW'
  | 'CALL_RULE'
  | 'CALL_CONNECTOR'
  | 'THROW_EXCEPTION'
  | 'RETURN'

/** 微流节点定义（DAG 画布节点数据模型） */
export interface MicroflowNode {
  id: string
  type: MicroflowNodeType
  label: string
  x: number
  y: number
  config: Record<string, unknown>
}

/** 微流连线定义（DAG 画布边数据模型） */
export interface MicroflowEdge {
  id: string
  source: string
  target: string
  sourcePort: string
  targetPort: string
}

/** 微流变量定义 */
export interface MicroflowVariable {
  name: string
  type: string
}

/** 微流变量集合 */
export interface MicroflowVariables {
  inputs: MicroflowVariable[]
  locals: MicroflowVariable[]
  returnType: string
}

/** 微流 definition JSON 顶层结构 */
export interface MicroflowDefinition {
  nodes: MicroflowNode[]
  edges: MicroflowEdge[]
  variables: MicroflowVariables
}

/** 微流执行日志单条记录（节点级轨迹） */
export interface MicroflowExecutionLog {
  id?: number
  microflowId?: number
  microflowCode?: string
  executionId: string
  nodeId: string
  nodeType: string
  startTime: string
  endTime?: string
  durationMs?: number
  inputs?: string
  outputs?: string
  variablesSnapshot?: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  errorMessage?: string
}

export function getMicroflowList() {
  return get<LowCodeMicroflow[]>('/api/lowcode/microflow')
}

export function getMicroflow(id: number) {
  return get<LowCodeMicroflow>(`/api/lowcode/microflow/${id}`)
}

export function saveMicroflow(data: LowCodeMicroflow) {
  return post<LowCodeMicroflow>('/api/lowcode/microflow', data)
}

export function deleteMicroflow(id: number) {
  return del(`/api/lowcode/microflow/${id}`)
}

export function executeMicroflow(code: string, inputs: Record<string, unknown>) {
  return post(`/api/lowcode/microflow/${code}/execute`, inputs)
}

/** 查询某次执行的节点级轨迹日志 */
export function getExecutionLogs(executionId: string) {
  return get<MicroflowExecutionLog[]>(`/api/lowcode/microflow-execution-log/${executionId}`)
}

// ===================== 微流断点调试 =====================

/** 调试步骤状态 */
export type DebugStepStatus = 'PAUSED' | 'COMPLETED' | 'FAILED'

/** 调试会话 */
export interface MicroflowDebugSession {
  sessionId: string
  microflowCode: string
  microflowId?: number
  definitionJson?: string
  inputs?: Record<string, unknown>
  breakpointNodeIds: string[]
  variables: Record<string, unknown>
  currentNodeId: string | null
  result?: unknown
  terminated: boolean
  lastActivityTime?: number
}

/** 单步执行结果 */
export interface MicroflowDebugStepResult {
  nodeId: string | null
  nodeType?: string
  status: DebugStepStatus
  variables: Record<string, unknown>
  result?: unknown
  nextNodeId: string | null
  errorMessage?: string
}

/** 调试启动请求体 */
export interface MicroflowDebugStartRequest {
  inputs?: Record<string, unknown>
  breakpointNodeIds?: string[]
}

/** 启动微流调试会话 */
export function startMicroflowDebug(code: string, req: MicroflowDebugStartRequest) {
  return post<MicroflowDebugSession>(`/api/lowcode/microflow/${code}/debug/start`, req)
}

/** 单步执行（step over） */
export function stepOverMicroflowDebug(sessionId: string) {
  return post<MicroflowDebugStepResult>(`/api/lowcode/microflow/debug/${sessionId}/step`)
}

/** 继续执行到下一断点 */
export function continueMicroflowDebug(sessionId: string) {
  return post<MicroflowDebugStepResult>(`/api/lowcode/microflow/debug/${sessionId}/continue`)
}

/** 查询当前变量状态 */
export function getMicroflowDebugVariables(sessionId: string) {
  return get<Record<string, unknown>>(`/api/lowcode/microflow/debug/${sessionId}/variables`)
}

/** 终止微流调试会话 */
export function terminateMicroflowDebug(sessionId: string) {
  return del<void>(`/api/lowcode/microflow/debug/${sessionId}`)
}

/** 查询某微流最近若干次执行日志（按时间倒序） */
export function getRecentExecutionLogs(microflowId: number, limit = 10) {
  return get<MicroflowExecutionLog[]>(
    `/api/lowcode/microflow-execution-log/recent?microflowId=${microflowId}&limit=${limit}`
  )
}

// ===================== 微流图渲染（批次3-T6） =====================

/**
 * 构建微流 SVG 图直链 URL（用于 `<img :src>` 直接展示）。
 *
 * <p>注意：该端点需 lowcode:microflow:list 权限，
 * 直接用于 `<img>` 标签时需确保浏览器已携带 token（通过 cookie 或同源 session）。</p>
 *
 * @param id 微流 ID
 * @returns SVG 端点相对路径
 */
export function getMicroflowDiagramSvgUrl(id: number): string {
  return `/api/lowcode/microflow/${id}/diagram.svg`
}

/**
 * 构建微流 PNG 图直链 URL（用于 `<img :src>` 直接展示）。
 *
 * @param id 微流 ID
 * @returns PNG 端点相对路径
 */
export function getMicroflowDiagramPngUrl(id: number): string {
  return `/api/lowcode/microflow/${id}/diagram.png`
}

/**
 * 以 blob 形式下载微流 SVG 图，并触发浏览器下载。
 *
 * @param id 微流 ID
 */
export async function downloadMicroflowDiagramSvg(id: number): Promise<void> {
  const response = await get<Blob>(`/api/lowcode/microflow/${id}/diagram.svg`, undefined, {
    responseType: 'blob'
  })
  triggerBlobDownload(response, `microflow-${id}.svg`)
}

/**
 * 以 blob 形式下载微流 PNG 图，并触发浏览器下载。
 *
 * @param id 微流 ID
 */
export async function downloadMicroflowDiagramPng(id: number): Promise<void> {
  const response = await get<Blob>(`/api/lowcode/microflow/${id}/diagram.png`, undefined, {
    responseType: 'blob'
  })
  triggerBlobDownload(response, `microflow-${id}.png`)
}

