/**
 * iframe/远程组件 URL 安全校验（批次4-T7）。
 *
 * <p>从 sdk/runtime.ts 抽出的独立工具函数，供 {@link ComponentSandbox} 与
 * {@link initRemoteComponents} 共用，避免重复实现。</p>
 *
 * <p>规则：
 * <ul>
 *   <li>开发环境：允许 http://localhost:* 和 https://localhost:*</li>
 *   <li>生产环境：仅允许 https:// 开头</li>
 *   <li>禁止 file: / data: / javascript: / blob: 等危险协议</li>
 *   <li>域名白名单：可在 window.__LOWCODE_CSP_ALLOWLIST__ 中配置（数组），
 *       支持 *.example.com 通配符</li>
 *   <li>未配置白名单时，开发环境允许所有 localhost，生产允许所有 https</li>
 * </ul>
 * </p>
 */

/** CSP 白名单配置的全局变量名 */
const CSP_ALLOWLIST_KEY = '__LOWCODE_CSP_ALLOWLIST__'

/**
 * 读取 CSP 白名单配置。
 *
 * <p>白名单在应用启动时通过 `window.__LOWCODE_CSP_ALLOWLIST__ = ['cdn.example.com', '*.pms.com']`
 * 设置，通常来自后端配置或环境变量。</p>
 */
export function getCspAllowlist(): string[] {
  const list = (window as any)[CSP_ALLOWLIST_KEY] as string[] | undefined
  return Array.isArray(list) ? list : []
}

/**
 * 设置 CSP 白名单配置（供应用启动时调用）。
 */
export function setCspAllowlist(patterns: string[]): void {
  (window as any)[CSP_ALLOWLIST_KEY] = patterns
}

/**
 * 校验 URL 是否在允许范围内（CSP 白名单的运行时防御）。
 *
 * @param url 待校验的 URL
 * @returns 是否允许加载
 */
export function isAllowedUrl(url: string): boolean {
  if (!url || typeof url !== 'string') return false
  const lower = url.toLowerCase()
  // 禁止危险协议（file/data/javascript/blob 等）
  if (
    lower.startsWith('file:') ||
    lower.startsWith('data:') ||
    lower.startsWith('javascript:') ||
    lower.startsWith('blob:') ||
    lower.startsWith('vbscript:')
  ) {
    return false
  }
  const isDev = import.meta.env?.DEV === true
  // 开发环境允许 localhost http
  if (isDev && (lower.startsWith('http://localhost') || lower.startsWith('https://localhost'))) {
    return true
  }
  // 生产强制 https
  if (!isDev && !lower.startsWith('https://')) {
    return false
  }
  // 域名白名单（可选，未配置则放行所有 https）
  const allowlist = getCspAllowlist()
  if (allowlist.length > 0) {
    try {
      const host = new URL(url).hostname
      return allowlist.some((pattern) => {
        if (pattern.startsWith('*.')) {
          return host.endsWith(pattern.slice(1))
        }
        return host === pattern
      })
    } catch {
      return false
    }
  }
  return true
}

/**
 * 生成 iframe sandbox 属性值（批次4-T7）。
 *
 * <p>借鉴 ToolJet/Payload CMS 的 iframe 隔离策略，按"最小权限原则"组合 sandbox token：
 * <ul>
 *   <li>allow-scripts：允许执行 JS（必需，否则远程组件无法运行）</li>
 *   <li>allow-same-origin：允许同源访问（远程组件需 fetch 自己资源时必需，
 *       但与 allow-scripts 组合会降低隔离强度，仅在 trusted 场景启用）</li>
 *   <li>allow-forms：允许表单提交（表单类组件必需）</li>
 *   <li>allow-popups：允许 window.open（弹窗类组件）</li>
 *   <li>allow-modals：允许 alert/confirm（交互类组件）</li>
 *   <li>禁用 allow-top-navigation：禁止 iframe 重定向父页面（安全）</li>
 * </ul>
 * </p>
 *
 * @param sameOrigin 是否允许同源访问（默认 false，最大化隔离）
 * @param allowForms 是否允许表单提交
 * @param allowPopups 是否允许弹窗
 * @returns sandbox 属性值字符串（空格分隔的 token 列表）
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
  // 允许模态对话框（alert/confirm/prompt），交互类组件可能需要
  tokens.push('allow-modals')
  // 允许通过 postMessage 通信（sandbox 默认允许，显式声明便于文档化）
  return tokens.join(' ')
}

/**
 * 生成 iframe 的 CSP `Content-Security-Policy` 头（通过 meta 标签或 HTTP 头注入）。
 *
 * <p>由于 iframe 的 CSP 头无法由父页面直接设置（需目标页自身配置），
 * 本函数返回的 policy 字符串主要用于：
 * <ul>
 *   <li>父页面注入 iframe 时，作为 data-csp 属性记录期望策略（调试用）</li>
 *   <li>同源代理加载场景，由服务端按此 policy 注入 HTTP 头</li>
 * </ul>
 * 实际的 CSP 强制依赖目标页自身的 meta 标签或服务端 HTTP 头。</p>
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
