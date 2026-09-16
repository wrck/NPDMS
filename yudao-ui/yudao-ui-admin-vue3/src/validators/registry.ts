/**
 * Validator 注册中心
 * ===========================================================================
 *
 * 维护 URL 模式 → validator 的映射表。request.ts 拦截器在发送请求前，
 * 根据 method + url 从注册中心查找 validator，对请求体做校验。
 *
 * 设计要点
 * --------
 * - 支持「精确 URL」与「正则 URL」两种匹配方式
 * - 同一 URL 可分别注册 request validator 和 response validator
 * - 提供 `findValidator(method, url)` 供 request.ts 调用
 * - 各业务模块（如 validators/project.ts）在自己的模块末尾调用
 *   `registerValidator(...)` 完成注册，避免集中维护
 * ===========================================================================
 */
import type { ValidationResult } from './index'

/** Validator 函数签名 */
export type ValidatorFn<T = unknown> = (input: unknown) => ValidationResult<T>

/** HTTP 方法 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/** 注册项：一条 URL ↔ validator 的绑定 */
export interface ValidatorEntry {
  /** HTTP 方法（大小写不敏感） */
  method: HttpMethod
  /**
   * URL 模式：
   * - 字符串：精确匹配（如 `/api/project`）
   * - RegExp：正则匹配（如 `/^\/api\/project\/\d+$/`）
   *
   * 注意：URL 不包含 query string，匹配时已剥离 query。
   */
  urlPattern: string | RegExp
  /** 请求体校验器（POST/PUT/PATCH 时调用） */
  requestValidator?: ValidatorFn
  /** 响应数据校验器（对 Result.data 校验，可选） */
  responseValidator?: ValidatorFn
  /** 备注（用于调试） */
  description?: string
}

/** 注册表：所有已注册的 validator 条目 */
const registry: ValidatorEntry[] = []

/**
 * 注册一个 validator 条目。
 *
 * 后注册的条目优先级更高（在 findValidator 时从后向前匹配）。
 *
 * @example
 * ```ts
 * registerValidator({
 *   method: 'POST',
 *   urlPattern: '/api/project',
 *   requestValidator: projectValidator,
 *   description: '创建项目 - 校验请求体'
 * })
 * ```
 */
export function registerValidator(entry: ValidatorEntry): void {
  registry.push(entry)
}

/**
 * 批量注册 validator 条目。
 */
export function registerValidators(entries: ValidatorEntry[]): void {
  for (const e of entries) registry.push(e)
}

/**
 * 从 URL 中剥离 query string。
 *
 * `'/api/project/list?page=1&size=20'` → `'/api/project/list'`
 */
function stripQuery(url: string): string {
  const idx = url.indexOf('?')
  return idx >= 0 ? url.slice(0, idx) : url
}

/**
 * 判断请求 URL 是否匹配某条目。
 * - 字符串模式：精确匹配（剥离 query 后）
 * - 正则模式：regex.test(url)
 */
function matchUrl(pattern: string | RegExp, url: string): boolean {
  const cleanUrl = stripQuery(url)
  if (pattern instanceof RegExp) {
    return pattern.test(cleanUrl)
  }
  return pattern === cleanUrl
}

/**
 * 查找匹配的 validator 条目。
 *
 * 匹配规则：
 * 1. method 大小写不敏感匹配
 * 2. urlPattern 匹配（字符串精确 / 正则）
 * 3. 多个匹配时，取最后注册的一个（允许覆盖）
 *
 * @returns 匹配的条目数组（可能为空）。通常只取第一个。
 */
export function findValidators(
  method: string,
  url: string
): ValidatorEntry[] {
  const upperMethod = method.toUpperCase() as HttpMethod
  const matched: ValidatorEntry[] = []
  // 从后向前遍历，让后注册的优先
  for (let i = registry.length - 1; i >= 0; i--) {
    const entry = registry[i]
    if (entry.method === upperMethod && matchUrl(entry.urlPattern, url)) {
      matched.push(entry)
    }
  }
  return matched
}

/**
 * 查找第一个匹配的 request validator。
 *
 * @returns validator 函数；未找到返回 undefined
 */
export function findRequestValidator(
  method: string,
  url: string
): ValidatorFn | undefined {
  const entries = findValidators(method, url)
  for (const e of entries) {
    if (e.requestValidator) return e.requestValidator
  }
  return undefined
}

/**
 * 查找第一个匹配的 response validator。
 */
export function findResponseValidator(
  method: string,
  url: string
): ValidatorFn | undefined {
  const entries = findValidators(method, url)
  for (const e of entries) {
    if (e.responseValidator) return e.responseValidator
  }
  return undefined
}

/**
 * 清空注册表（仅用于测试）。
 */
export function clearRegistry(): void {
  registry.length = 0
}

/**
 * 获取注册表快照（仅用于调试/测试）。
 */
export function getRegistrySnapshot(): ValidatorEntry[] {
  return [...registry]
}
