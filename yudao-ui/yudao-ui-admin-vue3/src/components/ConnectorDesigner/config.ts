/**
 * 连接器配置（结构化 <-> JSON 字符串）转换工具。
 *
 * <p>分步表单内部使用 {@link ConnectorConfig} 结构化对象，保存到后端时序列化
 * 为 JSON 字符串存入 {@code LowCodeConnector.config}。加载时反向解析，并兼容
 * 旧版简单 JSON（如 {@code {"url":"","method":"GET"}}）。</p>
 */
import type {
  AuthType,
  ConnectorConfig,
  ConnectorType,
  HttpMethod,
  KeyValueItem,
  PaginationConfig,
  PaginationType,
  ResponseMapping,
  RestOperation,
  RetryConfig,
  SqlTemplate,
  SqlType
} from '@/api/lowcode-connector'

/** 默认重试配置 */
export const DEFAULT_RETRY: RetryConfig = {
  maxAttempts: 3,
  waitMillis: 1000,
  timeoutMillis: 30000,
  retryOnStatusCodes: [500, 502, 503]
}

/** 默认分页配置 */
export const DEFAULT_PAGINATION: PaginationConfig = {
  type: 'NONE'
}

/** 创建空 REST 操作 */
export function createEmptyRestOperation(): RestOperation {
  return {
    name: '',
    method: 'GET',
    path: '',
    headers: [],
    params: [],
    body: null
  }
}

/** 创建空 SQL 模板 */
export function createEmptySqlTemplate(): SqlTemplate {
  return {
    name: '',
    sqlType: 'QUERY',
    sqlTemplate: ''
  }
}

/** 创建空响应映射 */
export function createEmptyResponseMapping(): ResponseMapping {
  return {
    sourcePath: '',
    targetField: '',
    transform: null
  }
}

/** 创建默认配置（按类型） */
export function createDefaultConfig(type: ConnectorType): ConnectorConfig {
  if (type === 'DB') {
    return {
      type: 'DB',
      driverClassName: 'com.mysql.cj.jdbc.Driver',
      dbUrl: '',
      dbUsername: '',
      dbPassword: '',
      maxPoolSize: 10,
      sqlTemplates: [],
      responseMapping: [],
      retry: { ...DEFAULT_RETRY }
    }
  }
  return {
    type: 'REST',
    baseUrl: '',
    authType: 'NONE',
    operations: [],
    responseMapping: [],
    pagination: { ...DEFAULT_PAGINATION },
    retry: { ...DEFAULT_RETRY }
  }
}

/** 安全解析 JSON 字符串 */
function safeParse(json: string): any {
  if (!json) return null
  try {
    return JSON.parse(json)
  } catch {
    return null
  }
}

/**
 * 将旧版简单 JSON 配置转换为结构化 {@link ConnectorConfig}。
 *
 * <p>兼容场景：
 * <ul>
 *   <li>{@code {"url":"","method":"GET"}} — 旧版 REST 单操作</li>
 *   <li>{@code {"url":"jdbc:...","driverClassName":"..."}} — 旧版 DB</li>
 *   <li>已结构化的配置 — 直接返回（补全缺失字段）</li>
 * </ul></p>
 */
export function parseConnectorConfig(
  configStr: string | undefined,
  type: ConnectorType
): ConnectorConfig {
  const parsed = safeParse(configStr || '')
  if (!parsed || typeof parsed !== 'object') {
    return createDefaultConfig(type)
  }

  // 已是结构化配置（含 type 字段）
  if (parsed.type === 'REST' || parsed.type === 'DB') {
    return normalizeConfig(parsed as ConnectorConfig)
  }

  // 旧版 REST 简单 JSON：{"url":"","method":"GET",...}
  if (type === 'REST') {
    const method = (parsed.method || 'GET') as HttpMethod
    return {
      type: 'REST',
      baseUrl: parsed.baseUrl || parsed.url || '',
      authType: (parsed.authType || 'NONE') as AuthType,
      username: parsed.username,
      password: parsed.password,
      token: parsed.token,
      headerName: parsed.headerName,
      apiKey: parsed.apiKey,
      operations: Array.isArray(parsed.operations)
        ? parsed.operations.map(normalizeRestOperation)
        : [
            {
              name: 'default',
              method,
              path: parsed.path || '',
              headers: toKeyValueList(parsed.headers),
              params: toKeyValueList(parsed.params),
              body: parsed.body ?? null
            }
          ],
      responseMapping: Array.isArray(parsed.responseMapping)
        ? parsed.responseMapping.map(normalizeResponseMapping)
        : [],
      pagination: parsed.pagination
        ? normalizePagination(parsed.pagination)
        : { ...DEFAULT_PAGINATION },
      retry: parsed.retry ? normalizeRetry(parsed.retry) : { ...DEFAULT_RETRY }
    }
  }

  // 旧版 DB 简单 JSON：{"url":"jdbc:...","driverClassName":"..."}
  return {
    type: 'DB',
    driverClassName: parsed.driverClassName || 'com.mysql.cj.jdbc.Driver',
    dbUrl: parsed.dbUrl || parsed.url || '',
    dbUsername: parsed.dbUsername || parsed.username || '',
    dbPassword: parsed.dbPassword || parsed.password || '',
    maxPoolSize: parsed.maxPoolSize ?? 10,
    sqlTemplates: Array.isArray(parsed.sqlTemplates)
      ? parsed.sqlTemplates.map(normalizeSqlTemplate)
      : Array.isArray(parsed.operations)
        ? parsed.operations.map((op: any) => ({
            name: op.name || '',
            sqlType: (op.sqlType || 'QUERY') as SqlType,
            sqlTemplate: op.sqlTemplate || op.sql || ''
          }))
        : [],
    responseMapping: Array.isArray(parsed.responseMapping)
      ? parsed.responseMapping.map(normalizeResponseMapping)
      : [],
    retry: parsed.retry ? normalizeRetry(parsed.retry) : { ...DEFAULT_RETRY }
  }
}

/** 标准化结构化配置，补全缺失字段 */
function normalizeConfig(cfg: ConnectorConfig): ConnectorConfig {
  if (cfg.type === 'REST') {
    return {
      type: 'REST',
      baseUrl: cfg.baseUrl ?? '',
      authType: cfg.authType ?? 'NONE',
      username: cfg.username,
      password: cfg.password,
      token: cfg.token,
      headerName: cfg.headerName,
      apiKey: cfg.apiKey,
      operations: (cfg.operations || []).map(normalizeRestOperation),
      responseMapping: (cfg.responseMapping || []).map(normalizeResponseMapping),
      pagination: cfg.pagination ? normalizePagination(cfg.pagination) : { ...DEFAULT_PAGINATION },
      retry: cfg.retry ? normalizeRetry(cfg.retry) : { ...DEFAULT_RETRY }
    }
  }
  return {
    type: 'DB',
    driverClassName: cfg.driverClassName ?? 'com.mysql.cj.jdbc.Driver',
    dbUrl: cfg.dbUrl ?? '',
    dbUsername: cfg.dbUsername ?? '',
    dbPassword: cfg.dbPassword ?? '',
    maxPoolSize: cfg.maxPoolSize ?? 10,
    sqlTemplates: (cfg.sqlTemplates || []).map(normalizeSqlTemplate),
    responseMapping: (cfg.responseMapping || []).map(normalizeResponseMapping),
    retry: cfg.retry ? normalizeRetry(cfg.retry) : { ...DEFAULT_RETRY }
  }
}

function normalizeRestOperation(op: any): RestOperation {
  return {
    name: op.name || '',
    method: (op.method || 'GET') as HttpMethod,
    path: op.path || '',
    headers: toKeyValueList(op.headers),
    params: toKeyValueList(op.params),
    body: op.body ?? null
  }
}

function normalizeSqlTemplate(t: any): SqlTemplate {
  return {
    name: t.name || '',
    sqlType: (t.sqlType || 'QUERY') as SqlType,
    sqlTemplate: t.sqlTemplate || ''
  }
}

function normalizeResponseMapping(m: any): ResponseMapping {
  return {
    sourcePath: m.sourcePath || '',
    targetField: m.targetField || '',
    transform: m.transform ?? null
  }
}

function normalizePagination(p: any): PaginationConfig {
  return {
    type: (p.type || 'NONE') as PaginationType,
    offsetParam: p.offsetParam,
    limitParam: p.limitParam,
    totalCountPath: p.totalCountPath,
    pageParam: p.pageParam,
    pageSizeParam: p.pageSizeParam,
    totalPagesPath: p.totalPagesPath,
    nextLinkPath: p.nextLinkPath
  }
}

function normalizeRetry(r: any): RetryConfig {
  return {
    maxAttempts: r.maxAttempts ?? 3,
    waitMillis: r.waitMillis ?? 1000,
    timeoutMillis: r.timeoutMillis ?? 30000,
    retryOnStatusCodes: Array.isArray(r.retryOnStatusCodes)
      ? r.retryOnStatusCodes
      : [500, 502, 503]
  }
}

/** 将 headers/params 多种形态（对象 / KV 数组）统一为 KV 数组 */
function toKeyValueList(raw: any): KeyValueItem[] {
  if (!raw) return []
  if (Array.isArray(raw)) {
    return raw.map((kv: any) => ({
      key: kv.key ?? '',
      value: kv.value ?? ''
    }))
  }
  if (typeof raw === 'object') {
    return Object.entries(raw).map(([key, value]) => ({
      key,
      value: String(value ?? '')
    }))
  }
  return []
}

/** 序列化结构化配置为 JSON 字符串 */
export function serializeConnectorConfig(cfg: ConnectorConfig): string {
  return JSON.stringify(cfg, null, 2)
}
