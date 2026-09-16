/**
 * 低代码组件 SDK 公共入口（批次4-T6）。
 *
 * <p>本模块是面向第三方/自定义组件开发者的对外发布入口，借鉴 Power Apps PCF 与
 * ToolJet Component SDK 的设计：提供类型定义、注册函数、运行时上下文与生命周期 hook，
 * 使自定义组件能以与内置 Widget 一致的方式接入低代码平台。</p>
 *
 * <p>使用方式（自定义组件开发）：
 * <pre>
 * // MyWidget.ts
 * import { defineLowCodeComponent, type ComponentMeta, type LowCodeProps } from '@/sdk'
 *
 * export default defineLowCodeComponent({
 *   name: 'MyWidget',
 *   component: defineComponent({
 *     props: {
 *       modelValue: { type: [String, Number, Object], default: null },
 *       disabled: { type: Boolean, default: false }
 *     } as const satisfies LowCodeProps,
 *     emits: ['update:modelValue'],
 *     setup(props, { emit }) {
 *       // 业务逻辑
 *       return () => h('div', String(props.modelValue))
 *     }
 *   }),
 *   meta: {
 *     name: 'MyWidget',
 *     displayName: '我的组件',
 *     category: 'INPUT',
 *     propsSchema: [
 *       { key: 'disabled', type: 'boolean', default: false }
 *     ]
 *   }
 * })
 * </pre>
 * </p>
 *
 * <p>组件加载流程：
 * <ol>
 *   <li>本地内置组件：通过 {@link LowCodeComponentRegistry.initBuiltinComponents} 异步批量注册</li>
 *   <li>远程市场组件：从后端 /api/lowcode/component-meta 拉取，按 sourceType=MARKETPLACE
 *       的 entryUrl 动态 import（需 CORS/CSP 放行）</li>
 *   <li>自定义组件：业务代码 import 后调用 {@link register} 主动注册</li>
 * </ol>
 * </p>
 *
 * @see LowCodeComponentRegistry 实际注册中心实现
 */
export type {
  ComponentPropDef,
  ComponentMeta,
  RegisteredComponent,
  LowCodeProps,
  LowCodeEvent,
  LowCodeContext
} from './types'

export {
  defineLowCodeComponent,
  register,
  get,
  has,
  list,
  initBuiltinComponents,
  initRemoteComponents
} from './runtime'

// Guest 端运行时（批次4-T7，远程组件 iframe 内使用）
export { initGuest, autoReportHeight } from './guest-runtime'
export type { GuestConfig, GuestRuntime } from './guest-runtime'

// CSP 白名单工具（批次4-T7）
export {
  isAllowedUrl,
  buildSandboxAttribute,
  buildFrameCsp,
  getCspAllowlist,
  setCspAllowlist
} from './csp-allowlist'

/** SDK 版本（与 lowcode-platform-maturity-upgrade-design.md 批次4-T6/T7 对齐） */
export const SDK_VERSION = '1.0.0'
