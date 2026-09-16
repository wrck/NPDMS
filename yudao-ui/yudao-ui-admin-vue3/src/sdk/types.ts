/**
 * 低代码组件 SDK 类型定义（批次4-T6）。
 *
 * <p>本文件是 LowCodeComponentRegistry/types.ts 的超集，在原有 ComponentMeta /
 * ComponentPropDef / RegisteredComponent 之上，补充面向第三方开发者的：
 * <ul>
 *   <li>{@link LowCodeProps}：组件 props 类型规范（含必填 modelValue + 通用 disabled/readonly）</li>
 *   <li>{@link LowCodeEvent}：组件可触发的事件契约（update:modelValue / change / blur / focus）</li>
 *   <li>{@link LowCodeContext}：注入到组件 setup 上下文的运行时信息（表单数据/实体编码/用户信息）</li>
 * </ul>
 * </p>
 *
 * <p>自定义组件只需依赖本文件即可获得完整类型支持，无需依赖整个 lowcode-frontend 项目。
 * 配合 vite lib 模式打包，可作为独立 npm 包发布（计划中的 @pms/lowcode-sdk）。</p>
 */
import type {
  ComponentPropDef,
  ComponentMeta,
  RegisteredComponent
} from '@/components/LowCodeComponentRegistry/types'

// 重新导出基础类型，保持单一定义源
export type { ComponentPropDef, ComponentMeta, RegisteredComponent }

/**
 * 低代码组件通用 props 规范。
 *
 * <p>所有低代码组件都应满足此最小契约：
 * <ul>
 *   <li>modelValue：v-model 绑定值（必填，类型由具体组件决定）</li>
 *   <li>disabled：禁用态（表单只读模式下置 true）</li>
 *   <li>readonly：只读态（与 disabled 的区别：readonly 仍可复制，disabled 不可交互）</li>
 *   <li>placeholder：占位提示</li>
 * </ul>
 * </p>
 *
 * <p>自定义组件可通过 `as const satisfies LowCodeProps` 校验 props 定义合规性，
 * 借鉴 Power Apps PCF 的强类型 props 约束。</p>
 */
export interface LowCodeProps {
  /** v-model 绑定值（必填） */
  modelValue: {
    type: any
    default: any
  }
  /** 禁用态 */
  disabled?: {
    type: BooleanConstructor
    default: boolean
  }
  /** 只读态 */
  readonly?: {
    type: BooleanConstructor
    default: boolean
  }
  /** 占位提示 */
  placeholder?: {
    type: StringConstructor
    default: string
  }
  [key: string]: unknown
}

/**
 * 低代码组件标准事件契约。
 *
 * <p>组件除 update:modelValue 外，可选触发以下事件，由 LowCodeFormRenderer 统一监听
 * 并联动表单校验/联动逻辑（借鉴 Element Plus form-item 的 change/blur 钩子）。</p>
 */
export type LowCodeEvent =
  | 'update:modelValue'
  | 'change'
  | 'blur'
  | 'focus'
  | 'enter'

/**
 * 低代码组件运行时上下文（通过 provide/inject 注入到组件 setup）。
 *
 * <p>借鉴 ToolJet Component SDK 的运行时注入机制，使自定义组件能访问：
 * <ul>
 *   <li>formData：当前表单完整数据（用于联动判断）</li>
 *   <li>entityCode：当前实体编码（用于调用动态数据 API）</li>
 *   <li>formCode：当前表单编码</li>
 *   <li>userId / username：当前登录用户（用于权限相关组件如 UserSelector 默认值）</li>
 *   <li>mode：表单模式（DESIGN 设计态 / VIEW 查看态 / EDIT 编辑态）</li>
 * </ul>
 * </p>
 */
export interface LowCodeContext {
  /** 当前表单完整数据对象 */
  formData?: Record<string, unknown>
  /** 当前实体编码（动态实体场景） */
  entityCode?: string
  /** 当前表单编码 */
  formCode?: string
  /** 当前列表编码（列表渲染器场景） */
  listCode?: string
  /** 当前登录用户 ID */
  userId?: number | string
  /** 当前登录用户名 */
  username?: string
  /** 表单模式 */
  mode?: 'DESIGN' | 'VIEW' | 'EDIT'
  /** InjectionKey，便于 useLowCodeContext 强类型注入 */
  readonly __brand?: 'LowCodeContext'
}
