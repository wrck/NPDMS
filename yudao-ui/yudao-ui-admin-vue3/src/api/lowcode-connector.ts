import { del, get, post } from '@/utils/request'

export interface LowCodeConnector {
  id?: number
  code: string
  name: string
  description?: string
  type: 'REST' | 'DB'
  config: string
  status?: string
  bizType?: string
}

/** 连接器类型 */
export type ConnectorType = 'REST' | 'DB'

/** 认证类型 */
export type AuthType = 'NONE' | 'BASIC' | 'BEARER' | 'API_KEY'

/** HTTP 方法 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

/** SQL 操作类型 */
export type SqlType = 'QUERY' | 'UPDATE'

/** 分页类型 */
export type PaginationType = 'NONE' | 'OFFSET' | 'PAGE' | 'NEXT_LINK'

/** Key-Value 表行（用于 headers / params） */
export interface KeyValueItem {
  key: string
  value: string
}

/** REST 操作定义 */
export interface RestOperation {
  name: string
  method: HttpMethod
  path: string
  headers: KeyValueItem[]
  params: KeyValueItem[]
  body: string | null
}

/** DB SQL 模板定义 */
export interface SqlTemplate {
  name: string
  sqlType: SqlType
  sqlTemplate: string
}

/** 响应字段映射 */
export interface ResponseMapping {
  sourcePath: string
  targetField: string
  transform: string | null
}

/** 分页配置 */
export interface PaginationConfig {
  type: PaginationType
  offsetParam?: string
  limitParam?: string
  totalCountPath?: string
  pageParam?: string
  pageSizeParam?: string
  totalPagesPath?: string
  nextLinkPath?: string
}

/** 重试与超时配置 */
export interface RetryConfig {
  maxAttempts: number
  waitMillis: number
  timeoutMillis: number
  retryOnStatusCodes: number[]
}

/** DB 数据源配置 */
export interface DbDataSource {
  driverClassName: string
  url: string
  username: string
  password: string
  maxPoolSize: number
}

/** REST 认证配置 */
export interface RestAuth {
  authType: AuthType
  username?: string
  password?: string
  token?: string
  headerName?: string
  apiKey?: string
  baseUrl: string
}

/**
 * 连接器配置（结构化）。
 *
 * <p>分步表单收集后序列化为 JSON 字符串存到 {@link LowCodeConnector.config}。
 * 兼容旧版简单 JSON（如 {@code {"url":"","method":"GET"}}），加载时通过
 * {@link parseConnectorConfig} 转换为结构化形式。</p>
 */
export interface ConnectorConfig {
  type: ConnectorType
  /** REST */
  baseUrl?: string
  authType?: AuthType
  username?: string
  password?: string
  token?: string
  headerName?: string
  apiKey?: string
  operations?: RestOperation[]
  /** DB */
  driverClassName?: string
  dbUrl?: string
  dbUsername?: string
  dbPassword?: string
  maxPoolSize?: number
  sqlTemplates?: SqlTemplate[]
  /** 公共 */
  responseMapping?: ResponseMapping[]
  pagination?: PaginationConfig
  retry?: RetryConfig
}

/** 测试请求 payload */
export interface TestOperationPayload {
  operationName: string
  params?: Record<string, unknown>
  body?: unknown
}

/** 测试响应 */
export interface TestOperationResult {
  statusCode?: number
  headers?: Record<string, string>
  body?: unknown
  durationMillis?: number
  error?: string
  raw?: unknown
}

export function getConnectorList() {
  return get<LowCodeConnector[]>('/api/lowcode/connector')
}

export function getConnector(id: number) {
  return get<LowCodeConnector>(`/api/lowcode/connector/${id}`)
}

export function saveConnector(data: LowCodeConnector) {
  return post<LowCodeConnector>('/api/lowcode/connector', data)
}

export function deleteConnector(id: number) {
  return del(`/api/lowcode/connector/${id}`)
}

export function testConnector(code: string) {
  return post<any>(`/api/lowcode/connector/${code}/test`)
}

export function executeConnector(code: string, params: Record<string, any>) {
  return post<any>(`/api/lowcode/connector/${code}/execute`, params)
}

/**
 * 测试单个操作。
 *
 * <p>调用后端 {@code /api/lowcode/connector/{code}/test-operation} 接口，
 * 传入操作名与参数，返回响应详情（状态码、Headers、Body、耗时）。</p>
 */
export function testOperation(code: string, payload: TestOperationPayload) {
  return post<TestOperationResult>(`/api/lowcode/connector/${code}/test-operation`, payload)
}
