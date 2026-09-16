/**
 * ComponentSandbox Guest 端运行时（批次4-T7）。
 *
 * <p>供 iframe 内的远程组件页面引入，封装与父页面（host）的 postMessage 通信细节，
 * 使 iframe 内组件开发者无需关心协议细节，只需调用高阶 API。</p>
 *
 * <p>使用示例（iframe 内 HTML 页面）：
 * <pre>
 * import { connectToHost, type GuestHandle } from 'pms-lowcode-sdk/guest'
 *
 * const handle: GuestHandle = await connectToHost({
 *   componentName: 'ProgressIndicator',
 *   onInit: (props, context) => {
 *     renderProgressBar(props.value, props.status)
 *   },
 *   onPropsUpdate: (props) => {
 *     updateProgressBar(props.value, props.status)
 *   },
 *   onContextUpdate: (context) => {
 *     // 上下文变化处理
 *   },
 *   onResize: (width, height) => {
 *     layoutAdapt(width, height)
 *   }
 * })
 *
 * // 上报值变更（v-model）
 * handle.emitValue(newValue)
 * // 上报自定义事件
 * handle.emitEvent('change', [detail])
 * // 上报高度（自适应）
 * handle.reportHeight(document.body.scrollHeight)
 * // 上报错误
 * handle.reportError('加载失败', stack)
 * </pre>
 * </p>
 *
 * <p>注意：本文件需打包为独立产物发布到 CDN，供远程组件页面通过 script 标签或 ES Module 引入。
 * 不应直接在主应用中 import，主应用使用 {@link ComponentSandbox/index.vue}。</p>
 */
import {
  SANDBOX_PROTOCOL_VERSION,
  HostToGuestMessage,
  GuestToHostMessage,
  isSandboxMessage,
  createMessage,
  type SandboxMessage,
  type InitPayload,
  type UpdatePropsPayload,
  type UpdateContextPayload,
  type ResizePayload,
  type UpdateValuePayload,
  type EventPayload,
  type ReportHeightPayload,
  type ErrorPayload,
  type LogPayload
} from './protocol'

/** Guest 端配置 */
export interface GuestConfig {
  /** 组件名（与父页面 ComponentSandbox 的 componentName 对应） */
  componentName: string
  /** 收到 INIT 消息时回调（注入初始 props + context） */
  onInit?: (props: Record<string, unknown>, context: Record<string, unknown>) => void
  /** 收到 UPDATE_PROPS 时回调 */
  onPropsUpdate?: (props: Record<string, unknown>) => void
  /** 收到 UPDATE_CONTEXT 时回调 */
  onContextUpdate?: (context: Record<string, unknown>) => void
  /** 收到 RESIZE 时回调 */
  onResize?: (width: number, height: number) => void
  /** 收到 REQUEST_HEIGHT 时回调（应在此调用 reportHeight） */
  onRequestHeight?: () => void
  /** 是否自动上报高度（MutationObserver 监听 body 高度变化，默认 false） */
  autoReportHeight?: boolean
  /** 父页面 origin 白名单（默认 '*' 允许任意，生产应配置具体 origin） */
  allowedParentOrigin?: string
  /** 调试模式 */
  debug?: boolean
}

/** Guest 端句柄（连接建立后返回） */
export interface GuestHandle {
  /** 上报 v-model 值变更 */
  emitValue: (value: unknown) => void
  /** 上报自定义事件 */
  emitEvent: (eventName: string, args?: unknown[]) => void
  /** 上报自身内容高度（触发父容器自适应） */
  reportHeight: (height: number, width?: number) => void
  /** 上报运行时错误 */
  reportError: (message: string, stack?: string, source?: string) => void
  /** 上报日志 */
  reportLog: (level: LogPayload['level'], ...args: unknown[]) => void
  /** 断开连接（移除事件监听） */
  disconnect: () => void
}

/**
 * 连接到父页面（host），建立 postMessage 双向通信。
 *
 * <p>调用后会立即向父页面发送 READY 消息，父页面收到后回发 INIT 注入 props + context。</p>
 *
 * @param config Guest 端配置
 * @returns Guest 句柄（Promise，INIT 收到后 resolve）
 */
export function connectToHost(config: GuestConfig): Promise<GuestHandle> {
  const allowedOrigin = config.allowedParentOrigin || '*'
  let initialized = false
  const initResolveFns: Array<() => void> = []
  let mutationObserver: MutationObserver | null = null

  /** 向父页面发送消息 */
  function postToHost<T>(type: GuestToHostMessage, payload: T): void {
    const msg = createMessage(type, payload)
    // referrer origin 校验：若配置了 allowedParentOrigin，仅向该 origin 发送
    if (window.parent === window) {
      // 非 iframe 内，无法发送
      if (config.debug) console.warn('[GuestRuntime] 不在 iframe 内，消息丢弃', type)
      return
    }
    window.parent.postMessage(msg, allowedOrigin)
    if (config.debug) console.debug('[GuestRuntime] → host', type, payload)
  }

  /** 处理来自父页面的消息 */
  function onMessage(event: MessageEvent) {
    // origin 校验
    if (allowedOrigin !== '*' && event.origin !== allowedOrigin) {
      if (config.debug) {
        console.warn('[GuestRuntime] 忽略来源不匹配的消息', event.origin, '!=', allowedOrigin)
      }
      return
    }
    if (!isSandboxMessage(event.data)) return
    const msg = event.data as SandboxMessage
    if (config.debug) console.debug('[GuestRuntime] ← host', msg.type, msg.payload)

    switch (msg.type) {
      case HostToGuestMessage.INIT: {
        const payload = msg.payload as InitPayload
        initialized = true
        try {
          config.onInit?.(payload.props, payload.context)
        } catch (e) {
          reportError('onInit 回调异常', (e as Error)?.stack, 'onInit')
        }
        // resolve 所有等待 init 的 promise
        while (initResolveFns.length) initResolveFns.shift()!()
        // 启动自动高度上报
        if (config.autoReportHeight && !mutationObserver) {
          startAutoReportHeight()
        }
        break
      }
      case HostToGuestMessage.UPDATE_PROPS: {
        const payload = msg.payload as UpdatePropsPayload
        try {
          config.onPropsUpdate?.(payload.props)
        } catch (e) {
          reportError('onPropsUpdate 回调异常', (e as Error)?.stack, 'onPropsUpdate')
        }
        break
      }
      case HostToGuestMessage.UPDATE_CONTEXT: {
        const payload = msg.payload as UpdateContextPayload
        try {
          config.onContextUpdate?.(payload.context)
        } catch (e) {
          reportError('onContextUpdate 回调异常', (e as Error)?.stack, 'onContextUpdate')
        }
        break
      }
      case HostToGuestMessage.RESIZE: {
        const payload = msg.payload as ResizePayload
        try {
          config.onResize?.(payload.width, payload.height || 0)
        } catch (e) {
          reportError('onResize 回调异常', (e as Error)?.stack, 'onResize')
        }
        break
      }
      case HostToGuestMessage.REQUEST_HEIGHT: {
        try {
          config.onRequestHeight?.()
        } catch (e) {
          reportError('onRequestHeight 回调异常', (e as Error)?.stack, 'onRequestHeight')
        }
        // 默认行为：上报当前 body 高度
        if (!config.onRequestHeight) {
          reportHeight(document.body.scrollHeight)
        }
        break
      }
      default:
        if (config.debug) console.warn('[GuestRuntime] 未知消息类型', msg.type)
    }
  }

  /** 上报 v-model 值变更 */
  function emitValue(value: unknown): void {
    const payload: UpdateValuePayload = { value }
    postToHost(GuestToHostMessage.UPDATE_VALUE, payload)
  }

  /** 上报自定义事件 */
  function emitEvent(eventName: string, args?: unknown[]): void {
    const payload: EventPayload = { eventName, args }
    postToHost(GuestToHostMessage.EVENT, payload)
  }

  /** 上报高度 */
  function reportHeight(height: number, width?: number): void {
    const payload: ReportHeightPayload = { height, width }
    postToHost(GuestToHostMessage.REPORT_HEIGHT, payload)
  }

  /** 上报错误 */
  function reportError(message: string, stack?: string, source?: string): void {
    const payload: ErrorPayload = { message, stack, source }
    postToHost(GuestToHostMessage.ERROR, payload)
  }

  /** 上报日志 */
  function reportLog(level: LogPayload['level'], ...args: unknown[]): void {
    const payload: LogPayload = { level, args }
    postToHost(GuestToHostMessage.LOG, payload)
  }

  /** 自动高度上报（MutationObserver 监听 body 变化） */
  function startAutoReportHeight(): void {
    if (typeof MutationObserver === 'undefined') return
    let lastHeight = -1
    mutationObserver = new MutationObserver(() => {
      const h = document.body.scrollHeight
      if (h !== lastHeight && h > 0) {
        lastHeight = h
        reportHeight(h)
      }
    })
    mutationObserver.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['style', 'class']
    })
    // 首次上报
    reportHeight(document.body.scrollHeight)
  }

  // 注册消息监听
  window.addEventListener('message', onMessage)

  // 向父页面发送 READY
  postToHost(GuestToHostMessage.READY, {})

  const handle: GuestHandle = {
    emitValue,
    emitEvent,
    reportHeight,
    reportError,
    reportLog,
    disconnect: () => {
      window.removeEventListener('message', onMessage)
      if (mutationObserver) {
        mutationObserver.disconnect()
        mutationObserver = null
      }
    }
  }

  // 若已初始化则立即 resolve，否则等待 INIT
  if (initialized) {
    return Promise.resolve(handle)
  }
  return new Promise<GuestHandle>((resolve) => {
    initResolveFns.push(() => resolve(handle))
  })
}

/** 重新导出协议常量，便于 guest 端直接使用 */
export { SANDBOX_PROTOCOL_VERSION, HostToGuestMessage, GuestToHostMessage }
