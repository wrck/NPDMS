<script setup lang="ts">
/**
 * 低代码表单渲染引擎 V2（FormCreate 实现）。
 *
 * <p>V2 与 V1（{@code LowCodeFormRenderer}）保持完全相同的对外契约：</p>
 * <ul>
 *   <li>props：config（FormConfig）、modelValue（v-model）、disabled、
 *       componentRegistry、eventHandlers</li>
 *   <li>emits：update:modelValue / submit / validate-fail / field-change</li>
 *   <li>expose：validate / submit / resetFields / clearValidate / getFormData / formApi</li>
 *   <li>渲染能力：grid / tabs / collapse 三种布局；18 种字段类型 + custom
 *       自定义组件；required 校验合并；disabled / readonly / hidden 字段</li>
 * </ul>
 *
 * <p>差异仅在渲染底层：V1 直接以 Element Plus 组件渲染；
 * V2 将 {@link FormConfig} 翻译为 form-create（@form-create/element-ui）的
 * Rule[] 后交给 {@code <form-create>} 渲染，复用 yudao 底座统一的
 * form-create 渲染与校验体系。V1 原样保留，消费方可按需选择 V1 / V2。</p>
 *
 * <p>字段类型 → form-create rule.type 映射（与 @form-create/element-ui 内置组件名一致）：
 * input / textarea（input + type=textarea）/ number（inputNumber）/ password
 * （input + type=password）/ select / radio（radioGroup）/ checkbox（checkboxGroup）/
 * date / datetime / daterange（datePicker + type）/ switch / rate / slider（slider）/
 * cascader / upload；divider / title 为布局节点；custom 通过 form-create
 * component 注册（{@link registerCustomComponents}）。</p>
 */
import { computed, reactive, ref, watch, type Component } from 'vue'
import {
  FieldType,
  LayoutType,
  type FormConfig,
  type FormFieldConfig,
  type ResponsiveSpan
} from '@/api/lowcode'
import formCreate, { type Api as FormCreateApi, type Rule } from '@form-create/element-ui'

/** Props 定义（与 V1 完全一致） */
const props = withDefaults(
  defineProps<{
    /** 表单配置（解析后的 FormConfig 对象） */
    config: FormConfig
    /** 表单数据对象（v-model） */
    modelValue?: Record<string, unknown>
    /** 是否禁用整个表单 */
    disabled?: boolean
    /** 自定义组件注册表：key 为 props.componentName，value 为组件定义 */
    componentRegistry?: Record<string, Component>
    /** 事件处理器映射：key 为 field.events.change 值，value 为回调函数 */
    eventHandlers?: Record<string, (...args: unknown[]) => void>
  }>(),
  {
    disabled: false,
    componentRegistry: () => ({}),
    eventHandlers: () => ({})
  }
)

/** Emits 定义（与 V1 完全一致） */
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'submit', value: Record<string, unknown>): void
  (e: 'validate-fail', errors: unknown): void
  (e: 'field-change', field: FormFieldConfig, value: unknown): void
}>()

/** form-create v-model 绑定（读写代理到 formData，保持 V1 的数据流向） */
const values = computed<Record<string, unknown>>({
  get: () => formData,
  set: (val) => {
    if (!val) return
    for (const key of Object.keys(val)) {
      if (formData[key] !== val[key]) {
        formData[key] = val[key]
      }
    }
  }
})

/** form-create 实例（validate / resetFields / getValue 等通过 api 调用） */
const formApi = ref<FormCreateApi>()

/** 内部维护的表单数据（响应式，与 V1 相同的初始化语义） */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const formData = reactive<Record<string, any>>({ ...(props.modelValue || {}) })

/** 已注册到 form-create 的自定义组件名集合（避免重复注册） */
const registeredCustomComponents = new Set<string>()

/**
 * 初始化字段默认值：将 config.fields 中的 defaultValue 写入未提供的字段。
 * （与 V1 语义完全一致）
 */
function initDefaults() {
  for (const field of props.config.fields || []) {
    if (!(field.prop in formData)) {
      if (field.defaultValue !== undefined && field.defaultValue !== null) {
        formData[field.prop] = field.defaultValue
      } else if (field.type === FieldType.CHECKBOX) {
        formData[field.prop] = []
      } else {
        formData[field.prop] = ''
      }
    }
  }
}

// 监听 config 变化时重新初始化默认值
watch(
  () => props.config,
  () => initDefaults(),
  { immediate: true, deep: false }
)

// 监听外部 modelValue 变化，同步到内部 formData（与 V1 相同：不在此处回传，避免循环）
watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    for (const key of Object.keys(val)) {
      if (formData[key] !== val[key]) {
        formData[key] = val[key]
      }
    }
  },
  { deep: true }
)

// 内部数据变化时回传父组件
watch(
  formData,
  (val) => {
    emit('update:modelValue', { ...val })
  },
  { deep: true }
)

/** 可见字段（过滤 hidden=true，与 V1 相同） */
const visibleFields = computed(() =>
  (props.config.fields || []).filter((f) => !f.hidden)
)

/**
 * 将 type=custom 字段的自定义组件注册到 form-create 全局组件。
 *
 * <p>form-create 的 rule.type 既可指向全局注册的组件名；自定义组件在
 * 注册后即可被 rule.type 引用。与 V1 的 componentRegistry 解析语义一致：
 * 优先取 field.props.componentName，回退到 field.componentName。</p>
 */
function registerCustomComponents() {
  for (const field of props.config.fields || []) {
    if (field.type !== FieldType.CUSTOM) continue
    const name = (field.props?.componentName as string) || field.componentName || ''
    if (!name || registeredCustomComponents.has(name)) continue
    const comp = props.componentRegistry[name]
    if (!comp) {
      console.warn(`[LowCodeFormRendererV2] 未注册的自定义组件: ${name}`)
      continue
    }
    formCreate.component(name, comp)
    registeredCustomComponents.add(name)
  }
}

// 监听 config / componentRegistry 变化时重新注册自定义组件
watch(
  () => [props.config, props.componentRegistry],
  () => registerCustomComponents(),
  { immediate: true }
)

/**
 * 生成字段对应的 change 事件处理（与 V1 相同：emit + eventHandlers 回调）。
 */
function handleFieldChange(field: FormFieldConfig, value: unknown) {
  emit('field-change', field, value)
  const handlerName = field.events?.change
  if (handlerName && props.eventHandlers[handlerName]) {
    props.eventHandlers[handlerName](value, field, formData)
  }
}

/**
 * 将 FormFieldConfig 翻译为 form-create Rule。
 *
 * <p>校验规则：required 合并为 form-create 的 validate 数组，
 * 自定义 rules（el-form rules 格式）按 validateField 语义合并到
 * rule.validate。change 事件通过 form-create 的 emit 配置接入
 * {@link handleFieldChange}。</p>
 */
function toRule(field: FormFieldConfig): Rule {
  // ---- 布局节点：divider / title 不渲染 form-item ----
  if (field.type === FieldType.DIVIDER) {
    return {
      type: 'el-divider',
      props: {
        contentPosition:
          (field.props?.contentPosition as 'left' | 'center' | 'right') || 'center',
        borderStyle: (field.props?.borderStyle as string) || 'solid'
      },
      children: [field.label]
    }
  }
  if (field.type === FieldType.TITLE) {
    return { type: 'h3', class: ['form-title'], style: { fontSize: '16px' }, children: [field.label] }
  }

  // ---- 布局/数据节点公共属性 ----
  // 值不在 rule 上声明：与 V1 相同，值统一由 formData（v-model）流向渲染层，
  // 避免 rule.value 依赖 formData 导致每次键入触发 rule 重算与值回设。
  const common = {
    field: field.prop,
    title: field.label,
    props: {
      placeholder: field.placeholder,
      disabled: field.disabled,
      clearable: field.clearable,
      ...(field.props || {})
    } as Record<string, unknown>,
    validate: buildValidate(field),
    emit: ['change'],
    // change 事件回调：value 与 field 透传给 handleFieldChange（与 V1 相同）
    on: {
      change: (value: unknown) => handleFieldChange(field, value)
    }
  } as Rule

  switch (field.type) {
    case FieldType.INPUT:
      return { ...common, type: 'input' }
    case FieldType.TEXTAREA:
      return {
        ...common,
        type: 'input',
        props: {
          ...common.props,
          type: 'textarea',
          rows: (field.props?.rows as number) ?? 3
        }
      }
    case FieldType.PASSWORD:
      return { ...common, type: 'input', props: { ...common.props, type: 'password' } }
    case FieldType.NUMBER:
      return { ...common, type: 'inputNumber' }
    case FieldType.SELECT: {
      const options = (field.props?.options as Array<{ label: string; value: unknown }>) || []
      return {
        ...common,
        type: 'select',
        options: options.map((opt) => ({ label: opt.label, value: opt.value }))
      }
    }
    case FieldType.RADIO: {
      const options = (field.props?.options as Array<{ label: string; value: unknown }>) || []
      return {
        ...common,
        type: 'radioGroup',
        options: options.map((opt) => ({ label: opt.label, value: opt.value }))
      }
    }
    case FieldType.CHECKBOX: {
      const options = (field.props?.options as Array<{ label: string; value: unknown }>) || []
      return {
        ...common,
        type: 'checkboxGroup',
        options: options.map((opt) => ({ label: opt.label, value: opt.value }))
      }
    }
    case FieldType.DATE:
    case FieldType.DATETIME:
    case FieldType.DATERANGE:
      return {
        ...common,
        type: 'datePicker',
        props: {
          ...common.props,
          type: dateType(field),
          format: (field.props?.format as string) || undefined,
          valueFormat: (field.props?.valueFormat as string) || undefined
        }
      }
    case FieldType.SWITCH:
      return { ...common, type: 'switch' }
    case FieldType.RATE:
      return { ...common, type: 'rate' }
    case FieldType.SLIDER:
      return { ...common, type: 'slider' }
    case FieldType.CASCADER:
      return { ...common, type: 'cascader' }
    case FieldType.UPLOAD:
      return {
        ...common,
        type: 'upload',
        props: {
          ...common.props,
          action: (field.props?.action as string) || '/api/file/upload',
          limit: (field.props?.limit as number) || 5,
          accept: (field.props?.accept as string) || '',
          multiple: (field.props?.multiple as boolean) ?? false,
          listType: (field.props?.listType as string) || 'text'
        }
      }
    case FieldType.CUSTOM: {
      const name = (field.props?.componentName as string) || field.componentName || ''
      return { ...common, type: name, field: field.prop }
    }
    default:
      return { ...common, type: 'input' }
  }
}

/**
 * 生成字段的校验规则数组（form-create 的 rule.validate 格式）。
 *
 * <p>required 与 V1 相同：message 取 placeholder 或 `请填写${label}`，
 * trigger 为 blur + change；自定义 rules（el-form rules 格式）按
 * 顺序合并（不降低任何校验）。</p>
 */
function buildValidate(field: FormFieldConfig): Array<Record<string, unknown>> {
  const list: Array<Record<string, unknown>> = []
  if (field.required) {
    list.push({
      required: true,
      message: field.placeholder || `请填写${field.label}`,
      trigger: ['blur', 'change']
    })
  }
  if (field.rules && Array.isArray(field.rules)) {
    for (const r of field.rules) {
      list.push({ ...r })
    }
  }
  return list
}

/** 获取日期选择器的 type 属性（与 V1 相同） */
function dateType(field: FormFieldConfig): 'date' | 'datetime' | 'daterange' {
  if (field.type === FieldType.DATETIME) return 'datetime'
  if (field.type === FieldType.DATERANGE) return 'daterange'
  return 'date'
}

/**
 * 解析栅格 span 为 form-create col 绑定属性（与 V1 相同的兼容语义）。
 */
function colProps(span: number | ResponsiveSpan | undefined): Record<string, number> {
  if (span === undefined || typeof span === 'number') {
    return { span: span ?? 24 }
  }
  const result: Record<string, number> = {}
  if (span.xs !== undefined) result.xs = span.xs
  if (span.sm !== undefined) result.sm = span.sm
  if (span.md !== undefined) result.md = span.md
  if (span.lg !== undefined) result.lg = span.lg
  if (span.xl !== undefined) result.xl = span.xl
  return result
}

/** 布局配置 */
const layout = computed(() => props.config.layout || { type: LayoutType.GRID, gutter: 16 })

/** tabs / collapse 默认激活项（与 V1 相同） */
const activeTab = ref<string>('')
const activeCollapse = ref<string[]>([])

watch(
  layout,
  (val) => {
    if (val.type === LayoutType.TABS && val.tabs && val.tabs.length > 0 && !activeTab.value) {
      activeTab.value = val.tabs[0].name || val.tabs[0].title
    }
    if (val.type === LayoutType.COLLAPSE && val.collapse && val.collapse.length > 0) {
      activeCollapse.value = val.collapse.map((c, i) => c.name || String(i))
    }
  },
  { immediate: true }
)

/** 将字段 id 列表转为字段对象列表（过滤 hidden，与 V1 相同） */
function resolveFields(ids: string[]): FormFieldConfig[] {
  const map = new Map<string, FormFieldConfig>()
  for (const f of props.config.fields || []) {
    map.set(f.id, f)
  }
  return ids.map((id) => map.get(id)).filter((f): f is FormFieldConfig => !!f && !f.hidden)
}

/**
 * 按字段列表构建 rule 数组（row + col 包裹，与 V1 的 grid 布局语义一致）。
 */
function buildFieldRules(fields: FormFieldConfig[]): Rule[] {
  const children: Rule[] = []
  for (const field of fields) {
    if (field.type === FieldType.DIVIDER || field.type === FieldType.TITLE) {
      // 布局节点：不占栅格，直接追加
      children.push(toRule(field))
      continue
    }
    children.push({
      type: 'col',
      props: colProps(field.span) as unknown as Record<string, unknown>,
      children: [toRule(field)]
    })
  }
  return [
    {
      type: 'row',
      props: { gutter: layout.value.gutter ?? 16 } as unknown as Record<string, unknown>,
      children
    }
  ]
}

/** form-create rule 数组（grid 布局：全部可见字段） */
const rule = computed<Rule[]>(() => {
  if (!layout.value.type || layout.value.type === LayoutType.GRID) {
    return buildFieldRules(visibleFields.value)
  }
  return []
})

/** form-create option（关闭内置 submitBtn / resetBtn，由消费方自行控制提交） */
const option = computed<Record<string, unknown>>(() => ({
  form: {
    labelWidth: props.config.labelWidth ?? 100,
    labelPosition: props.config.labelPosition ?? 'right',
    size: props.config.size ?? 'default'
  },
  submitBtn: false,
  resetBtn: false
}))

/** form-create tabs 布局 rule（el-tabs + el-tab-pane + row/col 包裹；点击切换回写 activeTab） */
const tabsRule = computed<Rule[]>(() => {
  if (layout.value.type !== LayoutType.TABS) return []
  return [
    {
      type: 'el-tabs',
      props: { modelValue: activeTab.value } as unknown as Record<string, unknown>,
      on: {
        'update:modelValue': (value: string) => {
          activeTab.value = value
        }
      },
      children: (layout.value.tabs || []).map((tab) => ({
        type: 'el-tab-pane',
        props: {
          label: tab.title,
          name: tab.name || tab.title
        } as unknown as Record<string, unknown>,
        children: buildFieldRules(resolveFields(tab.fields))
      }))
    }
  ]
})

/** form-create collapse 布局 rule（el-collapse + el-collapse-item + row/col 包裹；展开/收起回写 activeCollapse） */
const collapseRule = computed<Rule[]>(() => {
  if (layout.value.type !== LayoutType.COLLAPSE) return []
  return [
    {
      type: 'el-collapse',
      props: { modelValue: activeCollapse.value } as unknown as Record<string, unknown>,
      on: {
        'update:modelValue': (value: string[]) => {
          activeCollapse.value = value
        }
      },
      children: (layout.value.collapse || []).map((group, idx) => ({
        type: 'el-collapse-item',
        props: {
          title: group.title,
          name: group.name || String(idx)
        } as unknown as Record<string, unknown>,
        children: buildFieldRules(resolveFields(group.fields))
      }))
    }
  ]
})

/** form-create 最终 rule（按布局类型合并；custom 组件已由 watch 统一注册） */
const finalRule = computed<Rule[]>(() => {
  if (layout.value.type === LayoutType.TABS) return tabsRule.value
  if (layout.value.type === LayoutType.COLLAPSE) return collapseRule.value
  return rule.value
})

/** 表单校验（通过 form-api validate，与 V1 相同：失败 emit validate-fail） */
async function validate(): Promise<boolean> {
  if (!formApi.value) return false
  try {
    await formApi.value.validate()
    return true
  } catch (errors) {
    emit('validate-fail', errors)
    return false
  }
}

/** 提交表单：先校验，通过后 emit submit（与 V1 相同） */
async function submit(): Promise<void> {
  const ok = await validate()
  if (ok) {
    emit('submit', { ...formData })
  }
}

/** 重置表单到初始值（与 V1 相同：重置默认值） */
function resetFields(): void {
  formApi.value?.resetFields()
  for (const field of props.config.fields || []) {
    if (field.defaultValue !== undefined) {
      formData[field.prop] = field.defaultValue
    } else {
      formData[field.prop] = field.type === FieldType.CHECKBOX ? [] : ''
    }
  }
}

/** 清除校验状态 */
function clearValidate(): void {
  formApi.value?.clearValidateState()
}

// 暴露方法供父组件通过 ref 调用（formApi 对应 V1 的 formRef）
defineExpose({
  validate,
  submit,
  resetFields,
  clearValidate,
  getFormData: () => ({ ...formData }),
  formApi
})
</script>

<template>
  <form-create
    v-model="values"
    v-model:api="formApi"
    :option="option"
    :rule="finalRule"
    :disabled="disabled"
    class="low-code-form-renderer-v2"
  />
</template>

<style scoped>
.low-code-form-renderer-v2 {
  width: 100%;
}
</style>
