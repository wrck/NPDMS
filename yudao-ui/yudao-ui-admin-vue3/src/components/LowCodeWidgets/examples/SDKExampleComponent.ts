/**
 * 组件 SDK 规范示例（批次4-T6）。
 *
 * <p>本文件演示如何用 Vue3 defineComponent + 类型化 props 按 SDK 规范开发自定义组件，
 * 并通过 defineLowCodeComponent 注册。可作为第三方组件开发者的参考模板。</p>
 *
 * <p>与 LowCodeWidgets/*.vue 的 `<script setup>` 风格的区别：
 * <ul>
 *   <li>本示例用 defineComponent（更显式的 props 类型 + setup 返回值），适合独立打包发布</li>
 *   <li>内置 Widget 用 script setup（更简洁，适合项目内开发）</li>
 *   <li>两种风格均通过 propsSchema + LowCodeComponentRegistry 注册，运行时一致</li>
 * </ul>
 * </p>
 *
 * <p>借鉴：Power Apps PCF 的 ComponentFramework.defineComponent、
 * ToolJet Component SDK 的 registerComponent。</p>
 */
import { defineComponent, h, type PropType } from 'vue'
import { defineLowCodeComponent } from '@/sdk'
import type { LowCodeEvent } from '@/sdk'

/**
 * 组件 props 类型定义（强类型，编译期检查）。
 *
 * <p>满足 LowCodeProps 契约：必填 modelValue + 通用 disabled/readonly/placeholder。
 * 业务字段 progress / status / showLabel 为本组件特有。</p>
 */
export interface ProgressIndicatorProps {
  /** v-model 绑定值（进度数值 0-100） */
  modelValue: number
  /** 禁用态 */
  disabled?: boolean
  /** 只读态 */
  readonly?: boolean
  /** 占位提示 */
  placeholder?: string
  /** 进度条颜色（success/warning/danger/primary） */
  status?: 'success' | 'warning' | 'danger' | 'primary'
  /** 是否显示百分比文字 */
  showLabel?: boolean
  /** 进度条高度（px） */
  height?: number
}

/** 组件事件（满足 LowCodeEvent 契约） */
export type ProgressIndicatorEmits = LowCodeEvent

/**
 * ProgressIndicator 组件定义（defineComponent 风格，强类型 props）。
 *
 * <p>通过 `as unknown as LowCodeProps` 在编译期关联 props 定义与 SDK 契约，
 * 借鉴 Power Apps PCF 的强类型 props 约束。</p>
 */
const ProgressIndicator = defineComponent({
  name: 'ProgressIndicator',
  props: {
    modelValue: {
      type: Number as unknown as PropType<number>,
      default: 0,
      required: true
    },
    disabled: { type: Boolean, default: false },
    readonly: { type: Boolean, default: false },
    placeholder: { type: String, default: '' },
    status: {
      type: String as PropType<'success' | 'warning' | 'danger' | 'primary'>,
      default: 'primary'
    },
    showLabel: { type: Boolean, default: true },
    height: { type: Number, default: 12 }
  },
  emits: ['update:modelValue', 'change'] as Array<ProgressIndicatorEmits>,
  setup(props) {
    const clamp = (v: number) => Math.max(0, Math.min(100, v))
    return () => {
      const value = clamp(Number(props.modelValue) || 0)
      const colorMap: Record<string, string> = {
        success: '#67c23a',
        warning: '#e6a23c',
        danger: '#f56c6c',
        primary: '#409eff'
      }
      const color = colorMap[props.status as string] || colorMap.primary
      return h('div', { class: 'lc-progress-indicator' }, [
        h(
          'div',
          {
            class: 'lc-progress-track',
            style: {
              width: '100%',
              height: (props.height as number) + 'px',
              background: '#ebeef5',
              borderRadius: '100px',
              overflow: 'hidden'
            }
          },
          [
            h('div', {
              class: 'lc-progress-fill',
              style: {
                width: value + '%',
                height: '100%',
                background: color,
                transition: 'width 0.3s ease',
                borderRadius: '100px'
              }
            })
          ]
        ),
        props.showLabel
          ? h(
              'span',
              { class: 'lc-progress-label', style: { marginLeft: '8px', fontSize: '12px' } },
              `${value}%`
            )
          : null
      ])
    }
  }
})

/**
 * 通过 SDK 注册组件（含 meta 与 propsSchema）。
 *
 * <p>propsSchema 与 component.props 一一对应，属性面板会据此渲染编辑控件。
 * 注册后可在表单设计器/列表设计器中拖拽使用，与内置 Widget 一致。</p>
 */
export default defineLowCodeComponent({
  name: 'ProgressIndicator',
  component: ProgressIndicator,
  meta: {
    name: 'ProgressIndicator',
    displayName: '进度指示器',
    category: 'DISPLAY',
    propsSchema: [
      {
        key: 'status',
        type: 'select',
        label: '进度状态',
        description: '进度条颜色，success=绿/warning=黄/danger=红/primary=蓝',
        default: 'primary',
        options: [
          { label: '主色（蓝）', value: 'primary' },
          { label: '成功（绿）', value: 'success' },
          { label: '警告（黄）', value: 'warning' },
          { label: '危险（红）', value: 'danger' }
        ]
      },
      {
        key: 'showLabel',
        type: 'boolean',
        label: '显示百分比',
        default: true
      },
      {
        key: 'height',
        type: 'number',
        label: '进度条高度(px)',
        default: 12,
        min: 4,
        max: 60,
        step: 2
      }
    ]
  }
})
