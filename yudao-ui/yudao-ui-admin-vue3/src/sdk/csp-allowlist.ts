/**
 * iframe/远程组件 URL 安全校验（批次4-T7）。
 *
 * 从 sdk/runtime.ts 抽出的独立工具函数，供 ComponentSandbox 与
 * initRemoteComponents 共用。
 *
 * 规则：开发环境可使用 localhost 的 HTTP 地址；其他地址必须为 HTTPS。
 * window.__LOWCODE_CSP_ALLOWLIST__ 可限制域名，支持 *.example.com；
 * 未配置白名单时仍保留允许有效 HTTPS 地址的既有行为。
 */

const CSP_ALLOWLIST_KEY = '__LOWCODE_CSP_ALLOWLIST__'

/** 读取应用启动时配置的远程组件域名白名单。 */
export function getCspAllowlist(): string[] {
  const list = (window as any)[CSP_ALLOWLIST_KEY] as string[] | undefined
  return Array.isArray(list) ? list : []
}

/** 设置 CSP 白名单配置（供应用启动时调用）。 */
export function setCspAllowlist(patterns: string[]): void {
  (window as any)[CSP_ALLOWLIST_KEY] = patterns
}

/** 校验远程组件的绝对 URL，协议和本机例外均依据解析结果而不是字符串前缀。 */
export function isAllowedUrl(url: string): boolean {
  if (!url || typeof url !== 'string') return false
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    return false
  }
  const isDev = import.meta.env?.DEV === true
  const webProtocol = parsed.protocol === 'https:' || parsed.protocol === 'http:'
  if (!webProtocol) return false
  // localhost.evil.com and localhost@evil.com are not local addresses.
  if (isDev && parsed.hostname === 'localhost') return true
  if (parsed.protocol !== 'https:') return false

  const allowlist = getCspAllowlist()
  if (allowlist.length === 0) return true
  return allowlist.some((pattern) => {
    if (pattern.startsWith('*.')) return parsed.hostname.endsWith(pattern.slice(1))
    return parsed.hostname === pattern
  })
}

/**
 * 生成 iframe sandbox 属性值（批次4-T7）。
 *
 * 按最小权限组合 sandbox token：allow-scripts 为必需；allow-same-origin
 * 仅在 trusted 场景启用，因为与 allow-scripts 组合会降低隔离强度。
 * 不添加 allow-top-navigation，避免 iframe 重定向父页面。
 *
 * @param sameOrigin 是否允许同源访问（默认 false）
 * @param allowForms 是否允许表单提交
 * @param allowPopups 是否允许弹窗
 */
export function buildSandboxAttribute(
  sameOrigin = false,
  allowForms = true,
  allowPopups = false
): string {
  const tokens: string[] = ['allow-scripts']
  if (sameOrigin) tokens.push('allow-same-origin')
  if (allowForms) tokens.push('allow-forms')
  if (allowPopups) tokens.push('allow-popups')
  tokens.push('allow-modals')
  return tokens.join(' ')
}

/**
 * 生成 iframe 的期望 CSP 配置。
 * 父页面 data-csp 只能记录期望策略；实际强制执行依赖目标页面的
 * Content-Security-Policy HTTP 响应头或受支持的 meta 策略。
 */
export function buildFrameCsp(allowedOrigins: string[]): string {
  const directives: string[] = [
    `default-src 'none'`,
    `script-src 'self' 'unsafe-inline' 'unsafe-eval' ${allowedOrigins.join(' ')}`.trim(),
    `style-src 'self' 'unsafe-inline' ${allowedOrigins.join(' ')}`.trim(),
    `img-src 'self' data: blob: ${allowedOrigins.join(' ')}`.trim(),
    `font-src 'self' data: ${allowedOrigins.join(' ')}`.trim(),
    `connect-src 'self' ${allowedOrigins.join(' ')}`.trim(),
    `frame-ancestors 'self'`,
    `form-action 'self' ${allowedOrigins.join(' ')}`.trim(),
    `base-uri 'self'`
  ]
  return directives.join('; ')
}
