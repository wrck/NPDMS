/**
 * Guest 端 SDK 运行时（批次4-T7）。
 *
 * <p>远程组件（iframe 内运行的代码）使用本模块与父页面（host）通信。
 * 提供与 host 端 {@link ComponentSandbox} 协议配套的 guest 端 API：
 * <ul>
 *   <li>{@link initGuest}：初始化 guest 运行时，监听 host 消息，上报 READY</li>
 *   <li>{@link getProps}：获取 host 注入的 props</li>
 *   <li>{@link getContext}：获取 host 注入的 LowCodeContext</li>
 *   <li>{@link updateValue}：上报 v-model 值变更（触发 host update:modelValue）</li>
 *   <li>{@link emitEvent}：上报自定义事件（change/blur/focus 等）</li>
 *   <li>{@link reportHeight}：上报自身内容高度（自适应场景）</li>
 *   <li>{@link reportError}：上报运行时错误</li>
 *   <li>{@link onPropsChange}：订阅 props 变更（响应式推送）</li>
 * </ul>
 * </p>
 *
 * <p>远程组件典型用法：
 * <pre>
 * // entry.html 内联 JS 或独立 .js 文件
 * import { initGuest } from '@/sdk/guest-runtime'
 *
 * const { getProps, getContext, updateValue, onPropsChange } = initGuest({
 *   onInit: ({ props, context }) => {
 *     renderMyComponent(document.body, props, context)
 *   }
 * })
 *
 * onPropsChange((newProps) => {
 *   updateMyComponent(newProps)
 * })
 * </pre>
 * </p>
 *
 * <p>注意：本模块需打包进远程组件的 JS bundle 中（或通过 CDN 引入），
 * 不依赖 host 端的 vue/element-plus，是独立的轻量运行时。</p>
 */
import {
  HostToGuestMessage,
  GuestToHostMessage,
  createMessage,
  isSandboxMessage,
  type InitPayload,
  type UpdatePropsPayload,
  type UpdateContextPayload,
  type ResizePayload,
  type ErrorPayload,
  type LogPayload,
  type SandboxMessage
} from '@/components/ComponentSandbox/protocol'

/** Guest 初始化配置 */
export interface GuestConfig {
  /**
   * 收到 INIT 消息时回调（首次注入 props + context）。
   * 通常在此回调中渲染组件。
   */
  onInit?: (payload: InitPayload) => void
  /** 收到 UPDATE_PROPS 时回调（props 变更） */
  onPropsChange?: (payload: UpdatePropsPayload) => void
  /** 收到 UPDATE_CONTEXT 时回调（context 变更） */
  onContextChange?: (payload: UpdateContextPayload) => void
  /** 收到 RESIZE 时回调（容器尺寸变化） */
  onResize?: (payload: ResizePayload) => void
  /** 收到 REQUEST_HEIGHT 时回调（host 请求上报高度） */
  onRequestHeight?: () => void
}

/** Guest 运行时句柄 */
export interface GuestRuntime {
  /** 获取当前 props（INIT 后可用） */
  getProps: () => Record<string, unknown>
  /** 获取当前 context（INIT 后可用） */
  getContext: () => Record<string, unknown>
  /** 上报 v-model 值变更 */
  updateValue: (value: unknown) => void
  /** 上报自定义事件（change/blur/focus 等） */
  emitEvent: (eventName: string, ...args: unknown[]) => void
  /** 上报自身内容高度（自适应场景） */
  reportHeight: (height: number, width?: number) => void
  /** 上报运行时错误 */
  reportError: (message: string, stack?: string, source?: string) => void
  /** 上报日志（调试用） */
  log: (level: LogPayload['level'], ...args: unknown[]) => void
  /** 订阅 props 变更（响应式推送） */
  onPropsChange: (handler: (payload: UpdatePropsPayload) => void) => void
  /** 订阅 context 变更 */
  onContextChange: (handler: (payload: UpdateContextPayload) => void) => void
  /** 销毁 guest 运行时（移除事件监听） */
  destroy: () => void
}

/**
 * 初始化 Guest 运行时。
 *
 * <p>调用后会：
 * <ol>
 *   <li>监听 window.message 事件（origin 校验 + 协议校验）</li>
 *   <li>立即向父页面发送 READY 消息，触发 host 推送 INIT</li>
 *   <li>收到 INIT 后调用 onInit 回调</li>
 * </ol>
 * </p>
 *
 * @param config 配置回调
 * @returns Guest 运行时句柄
 */
export function initGuest(config: GuestConfig = {}): GuestRuntime {
  let currentProps: Record<string, unknown> = {}
  let currentContext: Record<string, unknown> = {}

  // props/context 变更订阅者列表
  const propsChangeHandlers: Array<(payload: UpdatePropsPayload) => void> = []
  const contextChangeHandlers: Array<(payload: UpdateContextPayload) => void> = []

  // 动态追加 config 中的回调到订阅者列表
  if (config.onPropsChange) propsChangeHandlers.push(config.onPropsChange)
  if (config.onContextChange) contextChangeHandlers.push(config.onContextChange)

  /**
   * 向 host 发送消息。
   *
   * <p>targetOrigin 使用 document.referrer 的 origin（iframe 场景下 referrer 即父页面 URL），
   * 若 referrer 为空（如直接打开），回退到 '*'（仅开发场景，生产应配置明确 origin）。</p>
   */
  function postToHost(type: GuestToHostMessage, payload: unknown): void {
    let targetOrigin: string
    try {
      targetOrigin = document.referrer ? new URL(document.referrer).origin : '*'
    } catch {
      targetOrigin = '*'
    }
    const msg = createMessage(type, payload)
    window.parent.postMessage(msg, targetOrigin)
  }

  /**
   * 接收 host 消息（Host → Guest）。
   */
  function onMessage(event: MessageEvent) {
    // origin 校验：仅接受父页面消息
    if (window.parent !== event.source) return
    if (!isSandboxMessage(event.data)) return
    const msg = event.data as SandboxMessage

    switch (msg.type) {
      case HostToGuestMessage.INIT: {
        const payload = msg.payload as InitPayload
        currentProps = { ...payload.props }
        currentContext = { ...payload.context }
        config.onInit?.(payload)
        break
      }
      case HostToGuestMessage.UPDATE_PROPS: {
        const payload = msg.payload as UpdatePropsPayload
        currentProps = { ...payload.props }
        propsChangeHandlers.forEach((h) => h(payload))
        break
      }
      case HostToGuestMessage.UPDATE_CONTEXT: {
        const payload = msg.payload as UpdateContextPayload
        currentContext = { ...payload.context }
        contextChangeHandlers.forEach((h) => h(payload))
        break
      }
      case HostToGuestMessage.RESIZE: {
        const payload = msg.payload as ResizePayload
        config.onResize?.(payload)
        break
      }
      case HostToGuestMessage.REQUEST_HEIGHT:
        config.onRequestHeight?.()
        break
      default:
        break
    }
  }

  // 启动监听
  window.addEventListener('message', onMessage)

  // 立即上报 READY（触发 host 推送 INIT）
  postToHost(GuestToHostMessage.READY, {
    componentName: (window as any).__LC_COMPONENT_NAME__ || 'unknown',
    url: window.location.href
  })

  return {
    getProps: () => ({ ...currentProps }),
    getContext: () => ({ ...currentContext }),
    updateValue: (value) => {
      postToHost(GuestToHostMessage.UPDATE_VALUE, { value })
    },
    emitEvent: (eventName, ...args) => {
      postToHost(GuestToHostMessage.EVENT, { eventName, args })
    },
    reportHeight: (height, width) => {
      if (height > 0) {
        postToHost(GuestToHostMessage.REPORT_HEIGHT, { height, width })
      }
    },
    reportError: (message, stack, source) => {
      const payload: ErrorPayload = { message, stack, source }
      postToHost(GuestToHostMessage.ERROR, payload)
    },
    log: (level, ...args) => {
      const payload: LogPayload = { level, args }
      postToHost(GuestToHostMessage.LOG, payload)
    },
    onPropsChange: (handler) => {
      propsChangeHandlers.push(handler)
    },
    onContextChange: (handler) => {
      contextChangeHandlers.push(handler)
    },
    destroy: () => {
      window.removeEventListener('message', onMessage)
      propsChangeHandlers.length = 0
      contextChangeHandlers.length = 0
    }
  }
}

/**
 * 自动高度上报工具（批次4-T7）。
 *
 * <p>使用 ResizeObserver 监听 document.body 尺寸变化，自动通过 guest runtime 上报高度。
 * 适用于内容动态变化的组件（如富文本编辑器、动态列表）。</p>
 *
 * @param runtime guest 运行时
 * @returns 取消监听函数
 */
export function autoReportHeight(runtime: GuestRuntime): () => void {
  if (typeof ResizeObserver === 'undefined') return () => {}
  const observer = new ResizeObserver((entries) => {
    for (const entry of entries) {
      const height = Math.ceil(entry.contentRect.height)
      if (height > 0) {
        runtime.reportHeight(height, Math.ceil(entry.contentRect.width))
      }
    }
  })
  observer.observe(document.body)
  return () => observer.disconnect()
}
