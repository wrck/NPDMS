import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
// 启用数据集成校验对象：导入所有实体 validator（导入即注册到 registry）
// 顺序：业务核心 → 资产 → 实施/工作流 → 系统管理
import '@/validators/project'
import '@/validators/impl-task'
import '@/validators/milestone'
import '@/validators/punch-list'
import '@/validators/deliverable'
import '@/validators/change-request'
import '@/validators/risk'
import '@/validators/issue'
import '@/validators/asset'
import '@/validators/asset-model'
import '@/validators/asset-category'
import '@/validators/asset-transfer'
import '@/validators/warranty'
import '@/validators/rma'
import '@/validators/impl-progress'
import '@/validators/task-comment'
import '@/validators/task-checklist'
import '@/validators/task-dependency'
import '@/validators/settlement'
import '@/validators/agent'
import '@/validators/agent-score'
import '@/validators/workflow'
import '@/validators/approval-field-perm'
import '@/validators/sys-user'
import '@/validators/sys-role'
import '@/validators/sys-menu'
import '@/validators/sys-dept'
import '@/validators/sys-dict'
import '@/validators/sys-dict-item'
import '@/validators/sys-config'
import '@/validators/feedback'
import '@/validators/help-content'
import '@/validators/notification'
import { findRequestValidator, findResponseValidator } from '@/validators/registry'
import { formatErrors } from '@/validators'

/** localStorage key for the JWT token */
export const TOKEN_KEY = 'pms_token'

/** 幂等性请求头名称：与后端 IdempotentAspect 约定一致 */
export const IDEMPOTENT_KEY_HEADER = 'X-Idempotent-Key'

/** 需要注入幂等键的 HTTP 方法（写操作） */
const IDEMPOTENT_METHODS = ['post', 'put', 'delete', 'patch']

/** 写操作的 HTTP 方法（请求体需要校验） */
const WRITE_METHODS = ['post', 'put', 'patch']

/**
 * 开关：是否启用数据集成校验对象。
 *
 * - true（默认）：写操作请求体校验失败时阻止请求发送，避免触发后端 400。
 * - false：跳过校验（仅在调试时使用，生产环境不应关闭）。
 *
 * 也可在请求 config 中通过 `skipValidate: true` 单次跳过校验。
 */
export const VALIDATOR_ENABLED = true

/**
 * Backend unified response envelope.
 *
 * <p>yudao 底座使用 {@code { code, msg, data }}（字段名为 `msg`），
 * 旧 PMS 使用 {@code { code, message, data }}（字段名为 `message`）。
 * 本拦截器同时兼容两者：优先读 `msg`，回退到 `message`。</p>
 */
export interface ApiResult<T = unknown> {
  code: number
  msg?: string
  message?: string
  data: T
  success?: boolean
  timestamp?: number
}

/**
 * 生成 UUID v4 字符串，用于幂等键。
 *
 * 优先使用浏览器原生 {@link crypto.randomUUID}（HTTPS/localhost 安全上下文），
 * 降级到手动 RFC 4122 v4 实现（适用于 jsdom 等不支持 randomUUID 的环境）。
 */
function generateIdempotentKey(): string {
  // 浏览器原生 API（需安全上下文：HTTPS 或 localhost）
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // 降级：手动生成 RFC 4122 v4 UUID
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)
    // 设置 version (4) 和 variant (10xx) 位
    bytes[6] = (bytes[6] & 0x0f) | 0x40
    bytes[8] = (bytes[8] & 0x3f) | 0x80
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'))
    return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10, 16).join('')}`
  }
  // 最终降级：基于 Date + Math.random（仅用于极端环境，碰撞概率极低）
  return `id-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

const service: AxiosInstance = axios.create({
  // Use the Vite dev-server proxy (/api -> http://localhost:8080)
  baseURL: '',
  timeout: 30000
})

// Request interceptor: inject the JWT token + idempotent key for write operations
// + 数据集成校验：写操作请求体先经 validator 校验，失败则阻止请求
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 写操作（POST/PUT/DELETE/PATCH）注入 X-Idempotent-Key 头
    // 配合后端 @Idempotent 注解实现接口幂等性，防止用户重复点击导致重复提交
    const method = (config.method || '').toLowerCase()
    if (IDEMPOTENT_METHODS.includes(method) && !config.headers[IDEMPOTENT_KEY_HEADER]) {
      config.headers[IDEMPOTENT_KEY_HEADER] = generateIdempotentKey()
    }

    // ============ 数据集成校验对象 ============
    // 写操作（POST/PUT/PATCH）：根据 method + url 从 registry 查找 request validator，
    // 校验请求体。校验失败 → ElMessage.error + reject，不发请求。
    // 这一层校验是后端 @Valid 的前端镜像，能在网络往返前捕获字段名缺失/必填/类型/范围错误。
    if (
      VALIDATOR_ENABLED &&
      WRITE_METHODS.includes(method) &&
      config.data != null &&
      !(config as AxiosRequestConfig & { skipValidate?: boolean }).skipValidate
    ) {
      const url = config.url || ''
      const validator = findRequestValidator(method, url)
      if (validator) {
        const result = validator(config.data)
        if (!result.valid) {
          const msg = `请求数据校验失败：${formatErrors(result.errors)}`
          // 控制台输出完整错误，方便开发者定位
          console.warn(`[validator] 请求被拦截: ${method.toUpperCase()} ${url}`, {
            errors: result.errors,
            data: config.data
          })
          ElMessage.error(msg)
          return Promise.reject(new Error(msg))
        }
        // 校验通过：用 normalize 后的数据替换原请求体
        // normalize 会做字段白名单过滤 + 旧字段名映射，确保发到后端的是规范数据
        if (result.data) {
          config.data = result.data
        }
      }
    }

    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor: unwrap the unified envelope and handle business errors
// + 数据集成校验：可选对响应 data 做字段白名单过滤
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data as ApiResult
    // Success: yudao 底座使用 code=0 表示成功（旧 PMS 使用 code=200，兼容两者）
    if (res.code === 0 || res.code === 200) {
      // ============ 数据集成校验对象（响应侧） ============
      // 若该 URL 注册了 response validator，对 Result.data 做白名单过滤，
      // 防止后端返回前端未预期的字段污染 store/视图状态。
      // 响应校验失败不阻断流程（仅控制台 warn），避免后端字段微调导致前端瘫痪。
      if (VALIDATOR_ENABLED && res.data != null) {
        const method = (response.config.method || '').toLowerCase()
        const url = response.config.url || ''
        const respValidator = findResponseValidator(method, url)
        if (respValidator && !(response.config as AxiosRequestConfig & { skipValidate?: boolean }).skipValidate) {
          const result = respValidator(res.data)
          if (!result.valid) {
            console.warn(`[validator] 响应数据校验失败: ${method.toUpperCase()} ${url}`, {
              errors: result.errors,
              data: res.data
            })
          } else if (result.data) {
            // 校验通过：用 normalize 后的数据替换（白名单过滤）
            res.data = result.data
          }
        }
      }
      return res.data as unknown as AxiosResponse
    }
    // Unauthorized: clear token and redirect to login
    if (res.code === 401) {
      ElMessage.error('登录状态已过期，请重新登录')
      localStorage.removeItem(TOKEN_KEY)
      router.push('/login')
      return Promise.reject(new Error(res.msg || res.message || '未授权'))
    }
    // Other business errors（silent 请求不弹错误提示）
    if (!(response.config as AxiosRequestConfig & { silent?: boolean }).silent) {
      ElMessage.error(res.msg || res.message || '请求失败')
    }
    return Promise.reject(new Error(res.msg || res.message || '请求失败'))
  },
  (error) => {
    // HTTP 401: 未登录或 token 过期，清除 token 并跳转登录页
    if (error?.response?.status === 401) {
      if (!(error?.config as AxiosRequestConfig & { silent?: boolean } | undefined)?.silent) {
        ElMessage.error('登录状态已过期，请重新登录')
      }
      localStorage.removeItem(TOKEN_KEY)
      router.push('/login')
      return Promise.reject(new Error('未登录或登录已过期'))
    }
    // Network / HTTP errors（silent 请求不弹错误提示）
    if (!(error?.config as AxiosRequestConfig & { silent?: boolean } | undefined)?.silent) {
      const msg = error?.response?.data?.message || error.message || '网络异常，请稍后重试'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

/**
 * Typed GET helper that resolves to the unwrapped data payload.
 *
 * <p>响应拦截器已剥离外层 {@link ApiResult} 信封，故此处的 {@code Promise<T>}
 * 即对应后端 {@code Result.data}，而非完整 envelope。若需 envelope，
 * 请直接调用 {@link service}（默认 axios 实例）。</p>
 *
 * <p>params 使用 {@code object} 而非 {@code Record<string, unknown>}：
 * TypeScript 接口（如 AssetListQuery）没有隐式索引签名，无法赋值给
 * {@code Record<string, unknown>}，故放宽为 {@code object} 以兼容所有查询 DTO。</p>
 */
export function get<T = unknown>(
  url: string,
  params?: object,
  config?: AxiosRequestConfig
): Promise<T> {
  return service.get(url, { params, ...config }) as unknown as Promise<T>
}

/** Typed POST helper that resolves to the unwrapped data payload */
export function post<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig
): Promise<T> {
  return service.post(url, data, config) as unknown as Promise<T>
}

/** Typed PUT helper that resolves to the unwrapped data payload */
export function put<T = unknown>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig
): Promise<T> {
  return service.put(url, data, config) as unknown as Promise<T>
}

/** Typed DELETE helper that resolves to the unwrapped data payload */
export function del<T = unknown>(
  url: string,
  params?: object,
  config?: AxiosRequestConfig
): Promise<T> {
  return service.delete(url, { params, ...config }) as unknown as Promise<T>
}

export default service
