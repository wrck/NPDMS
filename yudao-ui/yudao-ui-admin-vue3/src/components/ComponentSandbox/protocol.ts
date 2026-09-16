/**
 * ComponentSandbox postMessage 通信协议（批次4-T7）。
 *
 * <p>定义父页面（host）与 iframe（guest）之间的双向消息协议，借鉴 ToolJet iframe
 * 沙箱与 Power Apps PCF 的通信模式。所有消息通过 `window.postMessage` 传递，
 * 收发双方必须校验 origin 防止跨站脚本攻击。</p>
 *
 * <p>协议版本：v1（initial）</p>
 *
 * <h3>消息类型</h3>
 * <ul>
 *   <li>Host → Guest（父发子收）：
 *     <ul>
 *       <li>{@link HostToGuestMessage.INIT}：注入初始 props + context</li>
 *       <li>{@link HostToGuestMessage.UPDATE_PROPS}：props 变更同步（响应式 watch）</li>
 *       <li>{@link HostToGuestMessage.UPDATE_CONTEXT}：上下文变更（formData/mode 等）</li>
 *       <li>{@link HostToGuestMessage.RESIZE}：通知 iframe 容器尺寸变化</li>
 *       <li>{@link HostToGuestMessage.REQUEST_HEIGHT}：请求 iframe 上报自身高度（自适应）</li>
 *     </ul>
 *   </li>
 *   <li>Guest → Host（子发父收）：
 *     <ul>
 *       <li>{@link GuestToHostMessage.READY}：iframe 加载完成，请求初始 props</li>
 *       <li>{@link GuestToHostMessage.UPDATE_VALUE}：上报 v-model 值变更（update:modelValue）</li>
 *       <li>{@link GuestToHostMessage.EVENT}：上报自定义事件（change/blur/focus 等）</li>
 *       <li>{@link GuestToHostMessage.REPORT_HEIGHT}：上报自身内容高度（自适应）</li>
 *       <li>{@link GuestToHostMessage.ERROR}：上报运行时错误</li>
 *       <li>{@link GuestToHostMessage.LOG}：上报日志（调试用）</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 *
 * <h3>消息载荷结构</h3>
 * <p>所有消息统一为 {@link SandboxMessage} 结构，通过 `type` 字段区分。
 * `id` 字段用于请求-响应配对（可选），`payload` 携带具体数据。</p>
 */

/** 协议版本 */
export const SANDBOX_PROTOCOL_VERSION = '1.0' as const

/** Host → Guest 消息类型 */
export const HostToGuestMessage = {
  /** 注入初始 props + context（iframe READY 后父页面响应） */
  INIT: 'LC_SANDBOX_INIT',
  /** props 变更同步（父页面 props watch 触发） */
  UPDATE_PROPS: 'LC_SANDBOX_UPDATE_PROPS',
  /** 上下文变更（formData/mode 等 LowCodeContext 变化） */
  UPDATE_CONTEXT: 'LC_SANDBOX_UPDATE_CONTEXT',
  /** 通知 iframe 容器尺寸变化（响应式布局） */
  RESIZE: 'LC_SANDBOX_RESIZE',
  /** 请求 iframe 上报自身高度（自适应场景） */
  REQUEST_HEIGHT: 'LC_SANDBOX_REQUEST_HEIGHT'
} as const
export type HostToGuestMessage = (typeof HostToGuestMessage)[keyof typeof HostToGuestMessage]

/** Guest → Host 消息类型 */
export const GuestToHostMessage = {
  /** iframe 加载完成，请求初始 props */
  READY: 'LC_SANDBOX_READY',
  /** 上报 v-model 值变更 */
  UPDATE_VALUE: 'LC_SANDBOX_UPDATE_VALUE',
  /** 上报自定义事件（change/blur/focus 等） */
  EVENT: 'LC_SANDBOX_EVENT',
  /** 上报自身内容高度（自适应） */
  REPORT_HEIGHT: 'LC_SANDBOX_REPORT_HEIGHT',
  /** 上报运行时错误 */
  ERROR: 'LC_SANDBOX_ERROR',
  /** 上报日志（调试用） */
  LOG: 'LC_SANDBOX_LOG'
} as const
export type GuestToHostMessage = (typeof GuestToHostMessage)[keyof typeof GuestToHostMessage]

/** 协议消息统一结构 */
export interface SandboxMessage<T = unknown> {
  /** 协议版本（兼容性校验） */
  version: typeof SANDBOX_PROTOCOL_VERSION
  /** 消息类型 */
  type: HostToGuestMessage | GuestToHostMessage
  /** 消息 ID（请求-响应配对用，可选） */
  id?: string
  /** 消息时间戳（ms） */
  timestamp: number
  /** 消息载荷 */
  payload: T
}

/** INIT 消息载荷 */
export interface InitPayload {
  /** 组件名（注册 key） */
  componentName: string
  /** 初始 props 值 */
  props: Record<string, unknown>
  /** 运行时上下文 */
  context: Record<string, unknown>
}

/** UPDATE_PROPS 消息载荷 */
export interface UpdatePropsPayload {
  /** 变更后的完整 props（全量推送，简化协议） */
  props: Record<string, unknown>
}

/** UPDATE_CONTEXT 消息载荷 */
export interface UpdateContextPayload {
  /** 变更后的完整 context（全量推送） */
  context: Record<string, unknown>
}

/** UPDATE_VALUE 消息载荷（iframe 上报 v-model 变更） */
export interface UpdateValuePayload {
  /** 新值 */
  value: unknown
}

/** EVENT 消息载荷（iframe 上报自定义事件） */
export interface EventPayload {
  /** 事件名（change/blur/focus 等） */
  eventName: string
  /** 事件参数 */
  args?: unknown[]
}

/** REPORT_HEIGHT 消息载荷 */
export interface ReportHeightPayload {
  /** iframe 内容高度（px） */
  height: number
  /** iframe 内容宽度（px） */
  width?: number
}

/** RESIZE 消息载荷（父通知子容器尺寸变化） */
export interface ResizePayload {
  /** 容器宽度（px） */
  width: number
  /** 容器高度（px，可选，无固定高度时为 0） */
  height?: number
}

/** ERROR 消息载荷 */
export interface ErrorPayload {
  /** 错误消息 */
  message: string
  /** 错误堆栈（可选） */
  stack?: string
  /** 错误来源（component name / lifecycle hook） */
  source?: string
}

/** LOG 消息载荷 */
export interface LogPayload {
  /** 日志级别 */
  level: 'debug' | 'info' | 'warn' | 'error'
  /** 日志内容 */
  args: unknown[]
}

/**
 * 判断 MessageEvent 是否为合法的 ComponentSandbox 消息。
 *
 * <p>校验：
 * <ul>
 *   <li>data 是对象且含 version + type 字段</li>
 *   <li>version 与当前协议版本一致</li>
 *   <li>type 在已知枚举范围内</li>
 * </ul>
 * </p>
 */
export function isSandboxMessage(data: unknown): data is SandboxMessage {
  if (!data || typeof data !== 'object') return false
  const msg = data as Record<string, unknown>
  if (msg.version !== SANDBOX_PROTOCOL_VERSION) return false
  if (typeof msg.type !== 'string') return false
  const allTypes: readonly string[] = [...Object.values(HostToGuestMessage), ...Object.values(GuestToHostMessage)]
  return allTypes.includes(msg.type as string)
}

/**
 * 创建协议消息（自动填充 version + timestamp）。
 */
export function createMessage<T>(
  type: HostToGuestMessage | GuestToHostMessage,
  payload: T,
  id?: string
): SandboxMessage<T> {
  return {
    version: SANDBOX_PROTOCOL_VERSION,
    type,
    id,
    timestamp: Date.now(),
    payload
  }
}
